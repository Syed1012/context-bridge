package com.contextbridge.controller;

import com.contextbridge.model.ContextSnapshot;
import com.contextbridge.service.ContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP-style SSE endpoint.
 *
 * <p>
 * Implements a lightweight Model Context Protocol transport over Server-Sent
 * Events:
 * <ul>
 * <li>{@code GET  /mcp/sse} — opens an SSE stream; IDE agents connect
 * here.</li>
 * <li>{@code POST /mcp/checkpoint_state} — IDE agent sends a snapshot; we embed
 * + persist it.</li>
 * <li>{@code POST /mcp/restore_state} — IDE agent requests the best snapshot
 * for a project.</li>
 * </ul>
 *
 * <p>
 * The SSE stream pushes a {@code tool_definitions} event on connect so that
 * compliant
 * MCP clients can discover the available tools automatically.
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpSseController {

        private final ContextService contextService;

        /** Thread pool for SSE stream tasks (one thread per connected client). */
        private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

        // ── SSE Stream ─────────────────────────────────────────────────────────────

        /**
         * Opens an SSE connection and immediately pushes the MCP tool manifest.
         * The client (IDE agent) keeps this connection alive to receive future events.
         */
        @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter connectSse() {
                SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

                sseExecutor.execute(() -> {
                        try {
                                // Push tool definitions so MCP clients can auto-discover tools
                                emitter.send(SseEmitter.event()
                                                .name("tool_definitions")
                                                .data(mcpToolManifest()));

                                // Keep-alive comment every 30 s (prevents proxy timeouts)
                                while (!Thread.currentThread().isInterrupted()) {
                                        Thread.sleep(30_000);
                                        emitter.send(SseEmitter.event()
                                                        .comment("keep-alive"));
                                }
                        } catch (IOException | InterruptedException e) {
                                log.info("SSE client disconnected: {}", e.getMessage());
                                emitter.complete();
                        }
                });

                emitter.onTimeout(emitter::complete);
                emitter.onError(e -> log.warn("SSE error: {}", e.getMessage()));
                return emitter;
        }

        // ── MCP Tool Endpoints ─────────────────────────────────────────────────────

        /**
         * {@code checkpoint_state} tool — persists the active context snapshot.
         */
        @PostMapping("/checkpoint_state")
        public ResponseEntity<Map<String, Object>> checkpointState(
                        @RequestBody ContextSnapshot snapshot) {

                log.info("MCP tool: checkpoint_state called for project='{}'", snapshot.projectName());

                String docId = contextService.checkpointState(snapshot);

                return ResponseEntity.ok(Map.of(
                                "status", "ok",
                                "doc_id", docId,
                                "message", "Context snapshot persisted successfully."));
        }

        /**
         * {@code restore_state} tool — retrieves the most relevant snapshot for a
         * project.
         *
         * @param body JSON body with {@code project_name} field
         */
        @PostMapping("/restore_state")
        public ResponseEntity<?> restoreState(
                        @RequestBody Map<String, String> body) {

                String projectName = body.get("project_name");
                log.info("MCP tool: restore_state called for project='{}'", projectName);

                return contextService.restoreState(projectName)
                                .<ResponseEntity<?>>map(ResponseEntity::ok)
                                .orElse(ResponseEntity.ok(Map.of(
                                                "status", "not_found",
                                                "message", "No snapshot found for project: " + projectName)));
        }

        // ── Tool Manifest ──────────────────────────────────────────────────────────

        /** Builds the MCP tool manifest that is pushed to clients on connect. */
        private Map<String, Object> mcpToolManifest() {
                return Map.of(
                                "protocol", "mcp",
                                "version", "0.1",
                                "tools", java.util.List.of(
                                                Map.of(
                                                                "name", "checkpoint_state",
                                                                "description",
                                                                "Summarize current working context into a snapshot and persist it.",
                                                                "input_schema", Map.of(
                                                                                "type", "object",
                                                                                "required", java.util.List.of(
                                                                                                "project_name",
                                                                                                "session_id",
                                                                                                "current_goal"),
                                                                                "properties", Map.of(
                                                                                                "project_name",
                                                                                                Map.of("type", "string"),
                                                                                                "session_id",
                                                                                                Map.of("type", "string"),
                                                                                                "current_goal",
                                                                                                Map.of("type", "string"),
                                                                                                "active_files",
                                                                                                Map.of("type", "array",
                                                                                                                "items",
                                                                                                                Map.of("type", "string")),
                                                                                                "architectural_decisions",
                                                                                                Map.of("type", "string"),
                                                                                                "unresolved_issues",
                                                                                                Map.of("type", "string")))),
                                                Map.of(
                                                                "name", "restore_state",
                                                                "description",
                                                                "Retrieve the most relevant context snapshot for a project.",
                                                                "input_schema", Map.of(
                                                                                "type", "object",
                                                                                "required",
                                                                                java.util.List.of("project_name"),
                                                                                "properties", Map.of(
                                                                                                "project_name",
                                                                                                Map.of("type", "string"))))));
        }

        // ── Error Handling ────────────────────────────────────────────────────────

        /**
         * Catch Chroma parsing errors (empty collection) and return a graceful response
         * instead of a raw 500 stacktrace.
         */
        @ExceptionHandler({
                        org.springframework.web.client.UnknownContentTypeException.class,
                        org.springframework.http.converter.HttpMessageNotReadableException.class
        })
        public ResponseEntity<Map<String, String>> handleChromaErrors(Exception ex) {
                log.warn("MCP endpoint: Chroma error intercepted: {}", ex.getMessage());
                return ResponseEntity.ok(Map.of(
                                "status", "error",
                                "message",
                                "Vector store is not ready or empty. Please try again after checkpointing."));
        }
}
