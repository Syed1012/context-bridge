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

    /** Thread pool for SSE stream keep-alive tasks. */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

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
        log.info("[MCP] Client connected (sessionId={})", sessionId);

        emitter.onCompletion(() -> {
            activeEmitters.remove(sessionId);
            log.info("[MCP] Client disconnected (sessionId={})", sessionId);
        });
        emitter.onTimeout(() -> {
            activeEmitters.remove(sessionId);
            emitter.complete();
        });
        emitter.onError(e -> {
            activeEmitters.remove(sessionId);
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

        // Keep-alive pings every 30s
        sseExecutor.execute(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && activeEmitters.containsKey(sessionId)) {
                    Thread.sleep(30_000);
                    if (activeEmitters.containsKey(sessionId)) {
                        emitter.send(SseEmitter.event().comment("keep-alive"));
                    }
                }
            } catch (IOException | InterruptedException e) {
                log.debug("[MCP] Keep-alive stopped for sessionId={}", sessionId);
                activeEmitters.remove(sessionId);
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
        List<Map<String, Object>> tools = List.of(
                Map.of(
                        "name", "checkpoint_state",
                        "description",
                        "Persist the current working context as a snapshot. "
                                + "Call this when switching tasks or ending a session to preserve context for later.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", List.of("project_name", "session_id", "current_goal"),
                                "properties", Map.of(
                                        "project_name", Map.of("type", "string",
                                                "description", "Logical name of the project"),
                                        "session_id", Map.of("type", "string",
                                                "description", "Unique session identifier"),
                                        "current_goal", Map.of("type", "string",
                                                "description", "What you were actively working on"),
                                        "active_files", Map.of("type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "Critical file paths open or modified"),
                                        "architectural_decisions", Map.of("type", "string",
                                                "description", "Why specific patterns or tools were chosen"),
                                        "unresolved_issues", Map.of("type", "string",
                                                "description", "Bugs or pending tasks for the next session")))),
                Map.of(
                        "name", "restore_state",
                        "description",
                        "Retrieve the most relevant context snapshot for a project. "
                                + "Call this at the start of a session to resume previous work.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", List.of("project_name"),
                                "properties", Map.of(
                                        "project_name", Map.of("type", "string",
                                                "description", "Project name to retrieve context for")))));

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
                            .activeFiles(arguments.has("active_files")
                                    ? objectMapper.convertValue(arguments.get("active_files"),
                                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                                    : List.of())
                            .architecturalDecisions(arguments.path("architectural_decisions").asText(null))
                            .unresolvedIssues(arguments.path("unresolved_issues").asText(null))
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
