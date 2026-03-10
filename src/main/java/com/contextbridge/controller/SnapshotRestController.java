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
}
