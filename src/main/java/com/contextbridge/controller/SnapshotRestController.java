package com.contextbridge.controller;

import com.contextbridge.model.ContextSnapshot;
import com.contextbridge.service.ContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
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

                log.debug("[REST] GET /api/snapshots (filter: {})", project != null ? project : "all");
                return ResponseEntity.ok(contextService.listSnapshots(project));
        }

        /** Retrieve the most relevant snapshot for a project (for IDE integration). */
        @GetMapping("/restore")
        public ResponseEntity<?> restoreSnapshot(
                        @RequestParam String project) {

                log.debug("[REST] GET /api/snapshots/restore (project='{}')", project);
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

                log.info("[REST] POST /api/snapshots — saving snapshot for project='{}'", snapshot.projectName());
                String docId = contextService.checkpointState(snapshot);
                return ResponseEntity.ok(Map.of(
                                "status", "ok",
                                "doc_id", docId));
        }

        /**
         * Semantic search across all snapshots — the dashboard's search bar calls this.
         * Returns matching snapshots ordered by semantic relevance.
         *
         * @param q       natural language query (e.g. "session management implementation")
         * @param project optional project filter
         * @param limit   max results, defaults to 5
         */
        @GetMapping("/recall")
        public ResponseEntity<Map<String, Object>> recallContext(
                        @RequestParam String q,
                        @RequestParam(required = false) String project,
                        @RequestParam(defaultValue = "5") int limit) {

                log.info("[REST] GET /api/snapshots/recall — q='{}', project={}", q,
                                project != null ? project : "all");
                List<ContextSnapshot> results = contextService.recallContext(q, project, Math.min(limit, 20));
                return ResponseEntity.ok(Map.of(
                                "query", q,
                                "results_count", results.size(),
                                "snapshots", results));
        }
}
