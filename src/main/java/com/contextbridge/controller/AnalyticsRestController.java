package com.contextbridge.controller;

import com.contextbridge.service.ContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for dashboard analytics.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/stats} — per-project aggregate statistics.</li>
 *   <li>{@code GET /api/decisions?q=...} — search across all decision logs.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalyticsRestController {

    private final ContextService contextService;

    /**
     * Returns per-project statistics: total sessions, completion rates,
     * latest status, last active time, and tech stack.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("[REST] GET /api/stats");
        List<Map<String, Object>> projects = contextService.getProjectStats();
        return ResponseEntity.ok(Map.of(
                "total_projects", projects.size(),
                "projects", projects));
    }

    /**
     * Searches across all key_decisions_log entries.
     * Example: /api/decisions?q=chromadb
     * Returns matching decisions with project context.
     */
    @GetMapping("/decisions")
    public ResponseEntity<Map<String, Object>> searchDecisions(
            @RequestParam String q) {

        log.info("[REST] GET /api/decisions — q='{}'", q);
        List<Map<String, Object>> results = contextService.searchDecisions(q);
        return ResponseEntity.ok(Map.of(
                "query", q,
                "results_count", results.size(),
                "decisions", results));
    }
}
