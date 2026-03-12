package com.contextbridge.controller;

import com.contextbridge.model.ContextSnapshot;
import com.contextbridge.service.ContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP SSE Transport Controller.
 *
 * <p>Implements the Model Context Protocol (MCP) over HTTP with SSE transport:
 * <ol>
 *   <li>{@code GET  /mcp/sse}     — Opens an SSE stream. Immediately sends an
 *       {@code endpoint} event telling the client where to POST messages.</li>
 *   <li>{@code POST /mcp/message} — Receives JSON-RPC 2.0 requests from the MCP
 *       client (tool calls, initialization, etc.) and writes results back over
 *       the SSE stream.</li>
 * </ol>
 *
 * <p>The protocol flow:
 * <pre>
 *   Client ──GET /mcp/sse──▶  Server (opens SSE stream)
 *   Server ──SSE endpoint──▶  Client (sends message endpoint URL)
 *   Client ──POST /mcp/message──▶ Server (JSON-RPC: initialize, tools/list, tools/call)
 *   Server ──SSE message──▶ Client (JSON-RPC response over SSE)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpSseController {

    private final ContextService contextService;
    private final ObjectMapper objectMapper;

    /** Active SSE connections keyed by session ID. */
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /** Tracks when each session last had tool activity (for checkpoint reminders). */
    private final Map<String, Long> lastActivity = new ConcurrentHashMap<>();

    /** Thread pool for SSE stream keep-alive tasks. */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /** Auto-checkpoint reminder interval: 10 minutes of inactivity. */
    private static final long CHECKPOINT_REMINDER_MS = 10 * 60 * 1000;

    // ── MCP Protocol Constants ─────────────────────────────────────────────────
    private static final String JSONRPC_VERSION = "2.0";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "context-bridge";
    private static final String SERVER_VERSION = "0.1.0";

    // ── SSE Endpoint ───────────────────────────────────────────────────────────

    /**
     * Opens an SSE connection and immediately sends the {@code endpoint} event
     * so the MCP client knows where to POST JSON-RPC messages.
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        activeEmitters.put(sessionId, emitter);
        lastActivity.put(sessionId, System.currentTimeMillis());
        log.info("[MCP] Client connected (sessionId={})", sessionId);

        emitter.onCompletion(() -> {
            activeEmitters.remove(sessionId);
            lastActivity.remove(sessionId);
            log.info("[MCP] Client disconnected (sessionId={})", sessionId);
        });
        emitter.onTimeout(() -> {
            activeEmitters.remove(sessionId);
            emitter.complete();
        });
        emitter.onError(e -> {
            activeEmitters.remove(sessionId);
            lastActivity.remove(sessionId);
            log.warn("[MCP] SSE connection error (sessionId={}): {}", sessionId, e.getMessage());
        });

        // Send the endpoint event — this is the critical handshake.
        // The client will POST all JSON-RPC messages to this URL.
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/message?sessionId=" + sessionId));
            log.debug("[MCP] Sent endpoint handshake to sessionId={}", sessionId);
        } catch (IOException e) {
            log.error("[MCP] Failed to send endpoint handshake: {}. Client will not be able to communicate.", e.getMessage());
            emitter.completeWithError(e);
            activeEmitters.remove(sessionId);
            return emitter;
        }

        // Keep-alive pings every 30s + auto-checkpoint reminders after inactivity
        sseExecutor.execute(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && activeEmitters.containsKey(sessionId)) {
                    Thread.sleep(30_000);
                    if (!activeEmitters.containsKey(sessionId)) break;

                    // Keep-alive ping
                    emitter.send(SseEmitter.event().comment("keep-alive"));

                    // Check if a checkpoint reminder is needed
                    Long lastTime = lastActivity.get(sessionId);
                    if (lastTime != null) {
                        long elapsed = System.currentTimeMillis() - lastTime;
                        if (elapsed >= CHECKPOINT_REMINDER_MS) {
                            // Send a reminder as a JSON-RPC notification (no id = notification)
                            Map<String, Object> reminder = new LinkedHashMap<>();
                            reminder.put("jsonrpc", JSONRPC_VERSION);
                            reminder.put("method", "notifications/checkpoint_reminder");
                            reminder.put("params", Map.of(
                                    "message", "You've been working for a while without checkpointing. "
                                            + "Consider calling checkpoint_state to save your progress.",
                                    "minutes_since_last_activity", elapsed / 60_000));

                            String json = objectMapper.writeValueAsString(reminder);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(json));
                            log.info("[MCP] Sent checkpoint reminder (sessionId={}, idle={}min)",
                                    sessionId, elapsed / 60_000);

                            // Reset so we don't spam — only remind once per inactivity window
                            lastActivity.put(sessionId, System.currentTimeMillis());
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                log.debug("[MCP] Keep-alive stopped for sessionId={}", sessionId);
                activeEmitters.remove(sessionId);
                lastActivity.remove(sessionId);
            } catch (Exception e) {
                log.debug("[MCP] SSE loop error for sessionId={}: {}", sessionId, e.getMessage());
                activeEmitters.remove(sessionId);
                lastActivity.remove(sessionId);
            }
        });

        return emitter;
    }

    // ── JSON-RPC Message Endpoint ──────────────────────────────────────────────

    /**
     * Receives JSON-RPC 2.0 requests from the MCP client and dispatches them
     * to the appropriate handler. Responses are sent back via the SSE stream.
     */
    @PostMapping(path = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestParam String sessionId,
            @RequestBody JsonNode request) {

        String method = request.path("method").asText("");
        JsonNode id = request.path("id");
        JsonNode params = request.path("params");

        // Reset inactivity timer — any message means the session is active
        lastActivity.put(sessionId, System.currentTimeMillis());

        log.info("[MCP] \u2190 {} (sessionId={})", method, sessionId);

        Map<String, Object> response;
        try {
            response = switch (method) {
                case "initialize" -> handleInitialize(id);
                case "notifications/initialized" -> {
                    log.debug("[MCP] Client initialized (sessionId={})", sessionId);
                    yield null; // Notifications don't get a response
                }
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolCall(id, params);
                case "ping" -> handlePing(id);
                default -> {
                    log.warn("[MCP] Unsupported method '{}' \u2014 client may be using a newer protocol version", method);
                    yield jsonRpcError(id, -32601, "Method not found: " + method);
                }
            };
        } catch (Exception e) {
            log.error("[MCP] Error handling method '{}': {}", method, e.getMessage(), e);
            response = jsonRpcError(id, -32603, "Internal error: " + e.getMessage());
        }

        // Send response via SSE stream if we have one
        if (response != null) {
            sendSseResponse(sessionId, response);
        }

        // Also return HTTP 202 Accepted (MCP SSE transport spec)
        return ResponseEntity.accepted().build();
    }

    // ── MCP Method Handlers ────────────────────────────────────────────────────

    private Map<String, Object> handleInitialize(JsonNode id) {
        Map<String, Object> serverInfo = Map.of(
                "name", SERVER_NAME,
                "version", SERVER_VERSION);

        Map<String, Object> capabilities = Map.of(
                "tools", Map.of("listChanged", false));

        Map<String, Object> result = Map.of(
                "protocolVersion", MCP_PROTOCOL_VERSION,
                "serverInfo", serverInfo,
                "capabilities", capabilities);

        return jsonRpcResult(id, result);
    }

    private Map<String, Object> handlePing(JsonNode id) {
        return jsonRpcResult(id, Map.of());
    }

    private Map<String, Object> handleToolsList(JsonNode id) {
        // checkpoint_state properties — using LinkedHashMap because Map.of() is limited to 10 entries
        Map<String, Object> checkpointProps = new LinkedHashMap<>();
        checkpointProps.put("project_name", Map.of("type", "string",
                "description", "Logical name of the project"));
        checkpointProps.put("session_id", Map.of("type", "string",
                "description", "Unique session identifier"));
        checkpointProps.put("current_goal", Map.of("type", "string",
                "description", "What you were actively working on — be specific and detailed"));
        checkpointProps.put("conversation_summary", Map.of("type", "string",
                "description", "Comprehensive summary of the entire session: what was discussed, "
                        + "what was tried, what worked, what failed. This is the most valuable "
                        + "field for the next session to understand the full context."));
        checkpointProps.put("progress_status", Map.of("type", "string",
                "description", "Current status: 'completed', 'in_progress', 'blocked', or 'abandoned'"));
        checkpointProps.put("active_files", Map.of("type", "array",
                "items", Map.of("type", "string"),
                "description", "Critical file paths open or modified during this session"));
        checkpointProps.put("code_changes", Map.of("type", "array",
                "items", Map.of("type", "object",
                        "properties", Map.of(
                                "file", Map.of("type", "string", "description", "File path relative to project root"),
                                "action", Map.of("type", "string", "description", "One of: created, modified, deleted, renamed"),
                                "summary", Map.of("type", "string", "description", "One-line summary of what changed"),
                                "details", Map.of("type", "string", "description", "Detailed description: methods added, logic modified, configs changed. Be thorough — this lets the next session pick up exactly where you left off."))),
                "description", "Detailed list of every code change made. Include what was modified and WHY."));
        checkpointProps.put("key_decisions_log", Map.of("type", "array",
                "items", Map.of("type", "object",
                        "properties", Map.of(
                                "decision", Map.of("type", "string", "description", "What was decided"),
                                "rationale", Map.of("type", "string", "description", "Why this choice was made"),
                                "alternatives_considered", Map.of("type", "string", "description", "Other options considered"))),
                "description", "Technical/architectural decisions made during this session with rationale"));
        checkpointProps.put("tech_stack", Map.of("type", "array",
                "items", Map.of("type", "string"),
                "description", "Key technologies and versions in use, e.g. ['Spring Boot 3.5', 'Java 21', 'Next.js 16']"));
        checkpointProps.put("next_steps", Map.of("type", "array",
                "items", Map.of("type", "string"),
                "description", "Ordered list of what the next session should tackle first — a prioritized handoff checklist"));
        checkpointProps.put("related_snapshots", Map.of("type", "array",
                "items", Map.of("type", "string"),
                "description", "Doc IDs of prior snapshots this work builds on (for session chaining)"));
        // Legacy fields kept for backward compat
        checkpointProps.put("architectural_decisions", Map.of("type", "string",
                "description", "(Legacy) Why specific patterns or tools were chosen"));
        checkpointProps.put("unresolved_issues", Map.of("type", "string",
                "description", "(Legacy) Bugs or pending tasks — prefer next_steps instead"));

        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(Map.of(
                        "name", "checkpoint_state",
                        "description",
                        "Persist the current working context as a rich snapshot. "
                                + "Call this when switching tasks, ending a session, or PERIODICALLY during long sessions "
                                + "to prevent context loss. Include detailed code_changes and conversation_summary "
                                + "so the next session can pick up exactly where you left off.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", List.of("project_name", "session_id", "current_goal",
                                        "conversation_summary", "progress_status"),
                                "properties", checkpointProps)));
        tools.add(Map.of(
                        "name", "restore_state",
                        "description",
                        "Retrieve the most recent context snapshot for a project. "
                                + "Returns exactly what was stored — the latest checkpoint for this project. "
                                + "Call at session start to resume work, or mid-conversation if context is fading.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", List.of("project_name"),
                                "properties", Map.of(
                                        "project_name", Map.of("type", "string",
                                                "description", "Exact project name to retrieve context for")))));
        tools.add(Map.of(
                        "name", "recall",
                        "description",
                        "Search your AI memory using natural language. Finds relevant context snapshots "
                                + "across ALL projects using semantic search. Use this when a user says things like "
                                + "'remember how we implemented session management?' or 'find the auth work from last week'. "
                                + "Returns matching sessions with full code changes, decisions, and summaries.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", List.of("query"),
                                "properties", Map.of(
                                        "query", Map.of("type", "string",
                                                "description", "Natural language search — describe what you're looking for. "
                                                        + "Example: 'OAuth2 session management implementation'"),
                                        "project_name", Map.of("type", "string",
                                                "description", "Optional — filter results to a specific project"),
                                        "max_results", Map.of("type", "integer",
                                                "description", "Max snapshots to return (default 5, max 20)")))));

        return jsonRpcResult(id, Map.of("tools", tools));
    }

    private Map<String, Object> handleToolCall(JsonNode id, JsonNode params) {
        String toolName = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");

        log.info("[MCP] Calling tool '{}'", toolName);

        return switch (toolName) {
            case "checkpoint_state" -> {
                try {
                    ContextSnapshot snapshot = ContextSnapshot.builder()
                            .timestamp(Instant.now())
                            .projectName(arguments.path("project_name").asText(""))
                            .sessionId(arguments.path("session_id").asText(""))
                            .currentGoal(arguments.path("current_goal").asText(""))
                            .activeFiles(parseStringList(arguments, "active_files"))
                            // Legacy fields
                            .architecturalDecisions(arguments.path("architectural_decisions").asText(null))
                            .unresolvedIssues(arguments.path("unresolved_issues").asText(null))
                            // Enriched fields (v2)
                            .conversationSummary(arguments.path("conversation_summary").asText(null))
                            .progressStatus(arguments.path("progress_status").asText("in_progress"))
                            .techStack(parseStringList(arguments, "tech_stack"))
                            .nextSteps(parseStringList(arguments, "next_steps"))
                            .relatedSnapshots(parseStringList(arguments, "related_snapshots"))
                            .codeChanges(parseCodeChanges(arguments))
                            .keyDecisionsLog(parseKeyDecisions(arguments))
                            .build();

                    String docId = contextService.checkpointState(snapshot);
                    yield toolResult(id, "Context snapshot saved successfully. Doc ID: " + docId, false);
                } catch (Exception e) {
                    log.error("[MCP] Tool 'checkpoint_state' failed: {}", e.getMessage(), e);
                    yield toolResult(id, "Failed to save context: " + e.getMessage(), true);
                }
            }
            case "restore_state" -> {
                String projectName = arguments.path("project_name").asText("");
                if (projectName.isBlank()) {
                    yield toolResult(id, "Error: 'project_name' is required.", true);
                }

                Optional<ContextSnapshot> snapshot = contextService.restoreState(projectName);
                if (snapshot.isPresent()) {
                    try {
                        String json = objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(snapshot.get());
                        yield toolResult(id, json, false);
                    } catch (Exception e) {
                        yield toolResult(id, "Error serializing snapshot: " + e.getMessage(), true);
                    }
                } else {
                    yield toolResult(id, "No context snapshot found for project: " + projectName, false);
                }
            }
            case "recall" -> {
                String query = arguments.path("query").asText("");
                if (query.isBlank()) {
                    yield toolResult(id, "Error: 'query' is required. Describe what you're looking for.", true);
                }

                String projectFilter = arguments.path("project_name").asText(null);
                int maxResults = arguments.path("max_results").asInt(5);
                maxResults = Math.min(maxResults, 20); // cap at 20

                List<ContextSnapshot> results = contextService.recallContext(query, projectFilter, maxResults);
                if (results.isEmpty()) {
                    yield toolResult(id, "No matching context found for: " + query
                            + ". Note: only checkpointed sessions are searchable — "
                            + "make sure to call checkpoint_state during or after sessions.", false);
                }

                try {
                    String json = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(Map.of(
                                    "query", query,
                                    "results_count", results.size(),
                                    "snapshots", results));
                    yield toolResult(id, json, false);
                } catch (Exception e) {
                    yield toolResult(id, "Error serializing results: " + e.getMessage(), true);
                }
            }
            default -> {
                log.warn("[MCP] Unknown tool '{}' requested", toolName);
                yield jsonRpcError(id, -32602, "Unknown tool: " + toolName);
            }
        };
    }

    // ── SSE Response Sender ────────────────────────────────────────────────────

    private void sendSseResponse(String sessionId, Map<String, Object> response) {
        SseEmitter emitter = activeEmitters.get(sessionId);
        if (emitter == null) {
            log.warn("[MCP] No active SSE session for sessionId={} \u2014 response dropped (client may have disconnected)", sessionId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(response)));
            log.debug("[MCP] \u2192 Response sent (sessionId={})", sessionId);
        } catch (IOException e) {
            log.warn("[MCP] Failed to send response (sessionId={}): {}. Removing session.", sessionId, e.getMessage());
            activeEmitters.remove(sessionId);
        }
    }

    // ── JSON-RPC Helpers ───────────────────────────────────────────────────────

    private Map<String, Object> jsonRpcResult(JsonNode id, Map<String, Object> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("id", nodeToValue(id));
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(JsonNode id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("id", nodeToValue(id));
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private Map<String, Object> toolResult(JsonNode id, String text, boolean isError) {
        List<Map<String, Object>> content = List.of(Map.of(
                "type", "text",
                "text", text));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("isError", isError);

        return jsonRpcResult(id, result);
    }

    private Object nodeToValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        return node.asText();
    }

    // ── JSON Parsing Helpers ─────────────────────────────────────────────────

    private List<String> parseStringList(JsonNode args, String fieldName) {
        if (!args.has(fieldName) || !args.get(fieldName).isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(args.get(fieldName),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private List<ContextSnapshot.CodeChange> parseCodeChanges(JsonNode args) {
        if (!args.has("code_changes") || !args.get("code_changes").isArray()) {
            return List.of();
        }
        List<ContextSnapshot.CodeChange> changes = new ArrayList<>();
        for (JsonNode node : args.get("code_changes")) {
            changes.add(ContextSnapshot.CodeChange.builder()
                    .file(node.path("file").asText(""))
                    .action(node.path("action").asText("modified"))
                    .summary(node.path("summary").asText(""))
                    .details(node.path("details").asText(null))
                    .build());
        }
        return changes;
    }

    private List<ContextSnapshot.KeyDecision> parseKeyDecisions(JsonNode args) {
        if (!args.has("key_decisions_log") || !args.get("key_decisions_log").isArray()) {
            return List.of();
        }
        List<ContextSnapshot.KeyDecision> decisions = new ArrayList<>();
        for (JsonNode node : args.get("key_decisions_log")) {
            decisions.add(ContextSnapshot.KeyDecision.builder()
                    .decision(node.path("decision").asText(""))
                    .rationale(node.path("rationale").asText(""))
                    .alternativesConsidered(node.path("alternatives_considered").asText(null))
                    .build());
        }
        return decisions;
    }

    // ── Legacy REST Endpoints (backward compat with manual testing) ─────────

    /**
     * {@code checkpoint_state} — direct REST endpoint for manual/curl testing.
     */
    @PostMapping("/checkpoint_state")
    public ResponseEntity<Map<String, Object>> checkpointState(
            @RequestBody ContextSnapshot snapshot) {

        log.info("[REST] POST /mcp/checkpoint_state \u2014 project='{}'", snapshot.projectName());
        String docId = contextService.checkpointState(snapshot);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "doc_id", docId,
                "message", "Context snapshot persisted successfully."));
    }

    /**
     * {@code restore_state} — direct REST endpoint for manual/curl testing.
     */
    @PostMapping("/restore_state")
    public ResponseEntity<?> restoreState(
            @RequestBody(required = false) Map<String, String> body) {

        if (body == null || body.isEmpty() || body.get("project_name") == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message",
                    "Request body required. Send: {\"project_name\": \"your-project\"}"));
        }

        String projectName = body.get("project_name");
        log.info("[REST] POST /mcp/restore_state \u2014 project='{}'", projectName);

        return contextService.restoreState(projectName)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of(
                        "status", "not_found",
                        "message", "No snapshot found for project: " + projectName)));
    }
}
