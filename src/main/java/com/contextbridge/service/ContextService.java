package com.contextbridge.service;

import com.contextbridge.model.ContextSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core service orchestrating snapshot persistence and retrieval.
 *
 * <p>Flow for {@code checkpoint_state}:
 * <ol>
 *   <li>Serialize the snapshot to JSON (used as the document text).</li>
 *   <li>Spring AI auto-generates a text embedding via Ollama.</li>
 *   <li>The document + embedding are stored in Chroma DB.</li>
 * </ol>
 *
 * <p>Flow for {@code restore_state}:
 * <ol>
 *   <li>A similarity search is performed against Chroma DB using the project name.</li>
 *   <li>The most relevant snapshot document is deserialized and returned.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextService {

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    // ── Checkpoint ─────────────────────────────────────────────────────────────

    /**
     * Persists a context snapshot as an embedded document in the vector store.
     *
     * @param snapshot the structured context to save
     * @return the ID of the document stored in Chroma DB
     */
    public String checkpointState(ContextSnapshot snapshot) {
        log.info("[Snapshot] Saving — project='{}', session='{}', goal='{}'",
                snapshot.projectName(), snapshot.sessionId(), snapshot.currentGoal());

        String content = toJson(snapshot);

        // Build metadata map — Chroma indexes these for filtering and relevance
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("project_name",   snapshot.projectName());
        metadata.put("session_id",     snapshot.sessionId());
        metadata.put("timestamp",      snapshot.timestamp().toString());
        metadata.put("current_goal",   Optional.ofNullable(snapshot.currentGoal()).orElse(""));
        metadata.put("progress_status", Optional.ofNullable(snapshot.progressStatus()).orElse("in_progress"));
        // Include a truncated conversation_summary for search boost
        if (snapshot.conversationSummary() != null && !snapshot.conversationSummary().isBlank()) {
            String summary = snapshot.conversationSummary();
            metadata.put("conversation_summary",
                    summary.length() > 500 ? summary.substring(0, 500) + "..." : summary);
        }

        Document doc = new Document(content, metadata);

        vectorStore.add(List.of(doc));
        log.info("[Snapshot] Saved successfully (docId={})", doc.getId());
        return doc.getId();
    }

    /**
     * Retrieves the most recent snapshot for the given project.
     * Uses exact metadata filtering (NOT semantic similarity) to ensure
     * we return exactly what was stored, not fuzzy matches from other projects.
     *
     * @param projectName the project to retrieve context for
     * @return the most recent snapshot, or empty if none found
     */
    public Optional<ContextSnapshot> restoreState(String projectName) {
        log.info("[Snapshot] Searching for latest snapshot of project='{}'", projectName);

        try {
            // Use a neutral query — we only care about the metadata filter
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("context snapshot for project " + projectName)
                            .topK(10)
                            .similarityThreshold(0.0)
                            .filterExpression("project_name == '" + projectName + "'")
                            .build()
            );

            if (results.isEmpty()) {
                log.info("[Snapshot] No snapshot found for project='{}'", projectName);
                return Optional.empty();
            }

            // Pick the most recent by timestamp (not by similarity score)
            ContextSnapshot latest = results.stream()
                    .map(doc -> fromJson(doc.getText()))
                    .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                    .findFirst()
                    .orElse(null);

            if (latest != null) {
                log.info("[Snapshot] Restored latest snapshot for project='{}' (timestamp={})",
                        projectName, latest.timestamp());
            }

            return Optional.ofNullable(latest);
        } catch (Exception e) {
            log.warn("[Snapshot] Search failed for project='{}': {}. Is ChromaDB running?", projectName, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Recall (Semantic Search) ──────────────────────────────────────────────

    /**
     * Cross-project semantic search — finds snapshots whose content is
     * semantically similar to the given natural-language query.
     *
     * <p>This is where ChromaDB's vector search truly shines. Example queries:
     * <ul>
     *   <li>"How did I implement session management?"</li>
     *   <li>"Authentication with OAuth2"</li>
     *   <li>"Database migration strategies"</li>
     * </ul>
     *
     * @param query         natural-language search query
     * @param projectName   optional — filter to a specific project, or null for all projects
     * @param maxResults    maximum number of snapshots to return
     * @return matching snapshots ordered by semantic relevance
     */
    public List<ContextSnapshot> recallContext(String query, String projectName, int maxResults) {
        log.info("[Recall] Searching for '{}' (project={})", query,
                projectName != null ? projectName : "all");

        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(maxResults)
                    .similarityThreshold(0.3); // Only return genuinely relevant results

            if (projectName != null && !projectName.isBlank()) {
                builder.filterExpression("project_name == '" + projectName + "'");
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());
            log.info("[Recall] Found {} result(s)", results.size());

            return results.stream()
                    .map(doc -> fromJson(doc.getText()))
                    .toList();
        } catch (Exception e) {
            log.warn("[Recall] Search failed: {}. Is ChromaDB running?", e.getMessage());
            return List.of();
        }
    }

    // ── List ───────────────────────────────────────────────────────────────────

    /**
     * Returns all snapshots for a given project (no semantic ranking — used by the frontend list).
     */
    public List<ContextSnapshot> listSnapshots(String projectName) {
        String query = (projectName != null && !projectName.isBlank())
                ? "context snapshot for project " + projectName
                : "context snapshot";

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(50)
                .similarityThreshold(0.0); // return all matches regardless of score

        if (projectName != null && !projectName.isBlank()) {
            builder.filterExpression("project_name == '" + projectName + "'");
        }

        try {
            List<Document> docs = vectorStore.similaritySearch(builder.build());
            log.debug("[Snapshot] List query returned {} result(s)", docs.size());
            return docs.stream()
                    .map(doc -> fromJson(doc.getText()))
                    .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                    .toList();
        } catch (Exception e) {
            log.warn("[Snapshot] List query failed (ChromaDB may be unavailable or collection empty): {}", e.getMessage());
            return List.of();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String toJson(ContextSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ContextSnapshot", e);
        }
    }

    private ContextSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, ContextSnapshot.class);
        } catch (JsonProcessingException e) {
            log.error("[Snapshot] Failed to deserialize snapshot JSON — data may be corrupt: {}",
                    json.length() > 200 ? json.substring(0, 200) + "..." : json, e);
            throw new IllegalStateException("Failed to deserialize ContextSnapshot", e);
        }
    }
}
