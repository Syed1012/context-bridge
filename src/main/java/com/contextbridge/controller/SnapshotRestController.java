package com.contextbridge.controller;

import com.contextbridge.model.ContextSnapshot;
import com.contextbridge.service.ContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.UnknownContentTypeException;

import java.util.List;
import java.util.Map;

/**
 * Standard REST API consumed by the Next.js frontend dashboard.
 *
 * <p>
 * Endpoints:
 * <ul>
 * <li>{@code GET  /api/snapshots?project=...} — list snapshots (optionally
 * filtered by project).</li>
 * <li>{@code GET  /api/snapshots/restore?project=...} — get best snapshot for a
 * project.</li>
 * <li>{@code POST /api/snapshots} — save a new snapshot (convenience alias for
 * checkpoint).</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotRestController {

        private final ContextService contextService;

        /** List all snapshots, optionally filtered by project name. */
        @GetMapping
        public ResponseEntity<List<ContextSnapshot>> listSnapshots(
                        @RequestParam(required = false) String project) {

                log.debug("REST list snapshots project='{}'", project);
                return ResponseEntity.ok(contextService.listSnapshots(project));
        }

        /** Retrieve the most relevant snapshot for a project (for IDE integration). */
        @GetMapping("/restore")
        public ResponseEntity<?> restoreSnapshot(
                        @RequestParam String project) {

                log.debug("REST restore snapshot project='{}'", project);
                return contextService.restoreState(project)
                                .<ResponseEntity<?>>map(ResponseEntity::ok)
                                .orElse(ResponseEntity.ok(Map.of(
                                                "status", "not_found",
                                                "message", "No snapshot found for project: " + project)));
        }

        /** Save a new snapshot (can be called directly without going through MCP). */
        @PostMapping
        public ResponseEntity<Map<String, Object>> saveSnapshot(
                        @RequestBody ContextSnapshot snapshot) {

                log.info("REST checkpoint snapshot project='{}'", snapshot.projectName());
                String docId = contextService.checkpointState(snapshot);
                return ResponseEntity.ok(Map.of(
                                "status", "ok",
                                "doc_id", docId));
        }

        /**
         * Spring AI 1.0.0 throws UnknownContentTypeException or
         * HttpMessageNotReadableException
         * when Chroma DB returns an empty response for a similarity search on an empty
         * collection.
         * We catch these globally in this controller and gracefully return an empty
         * list.
         */
        @ExceptionHandler({
                        org.springframework.web.client.UnknownContentTypeException.class,
                        org.springframework.http.converter.HttpMessageNotReadableException.class
        })
        public ResponseEntity<List<ContextSnapshot>> handleEmptyChromaCollection(Exception ex) {
                log.warn("Intercepted Spring AI / Chroma empty collection parsing error: {}. Returning empty list.",
                                ex.getMessage());
                return ResponseEntity.ok(List.of());
        }
}
