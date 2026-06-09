package com.contextbridge.service;

import com.contextbridge.model.ContextSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ContextService contextService;

    @Captor
    private ArgumentCaptor<List<Document>> documentListCaptor;

    private ContextSnapshot sampleSnapshot;

    @BeforeEach
    void setUp() {
        sampleSnapshot = ContextSnapshot.builder()
                .timestamp(Instant.now())
                .projectName("test-project")
                .sessionId("session-123")
                .currentGoal("Implement authentication")
                .conversationSummary("Set up OAuth2 login flow")
                .progressStatus("in_progress")
                .activeFiles(List.of("src/main/java/Config.java"))
                .techStack(List.of("Java 21", "Spring Boot"))
                .keyDecisionsLog(List.of(
                        new ContextSnapshot.KeyDecision("Use OAuth2", "Industry standard", "Session tokens")))
                .nextSteps(List.of("Write integration tests"))
                .build();
    }

    // ── Checkpoint Tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkpointState")
    class CheckpointStateTests {

        @Test
        @DisplayName("should persist snapshot with correct metadata")
        void shouldSaveSnapshotWithMetadata() throws Exception {
            String json = "{\"projectName\": \"test-project\"}";
            when(objectMapper.writeValueAsString(any(ContextSnapshot.class))).thenReturn(json);

            String docId = contextService.checkpointState(sampleSnapshot);

            verify(vectorStore, times(1)).add(documentListCaptor.capture());
            List<Document> savedDocs = documentListCaptor.getValue();
            assertThat(savedDocs).hasSize(1);

            Document savedDoc = savedDocs.getFirst();
            assertThat(docId).isEqualTo(savedDoc.getId());
            assertThat(savedDoc.getText()).isEqualTo(json);

            Map<String, Object> metadata = savedDoc.getMetadata();
            assertThat(metadata)
                    .containsEntry("project_name", "test-project")
                    .containsEntry("session_id", "session-123")
                    .containsEntry("progress_status", "in_progress");
        }
    }

    // ── Restore Tests ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("restoreState")
    class RestoreStateTests {

        @Test
        @DisplayName("should return the most recent snapshot by timestamp")
        void shouldReturnMostRecentSnapshot() throws Exception {
            String json1 = "{\"ts\":\"2023-01-01T10:00:00Z\"}";
            String json2 = "{\"ts\":\"2023-01-01T12:00:00Z\"}";

            ContextSnapshot older = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2023-01-01T10:00:00Z"))
                    .projectName("test-project").sessionId("s1").build();
            ContextSnapshot newer = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2023-01-01T12:00:00Z"))
                    .projectName("test-project").sessionId("s2").build();

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(new Document(json1), new Document(json2)));
            when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(older);
            when(objectMapper.readValue(json2, ContextSnapshot.class)).thenReturn(newer);

            Optional<ContextSnapshot> result = contextService.restoreState("test-project");

            assertThat(result).isPresent();
            assertThat(result.get().sessionId()).isEqualTo("s2");
        }

        @Test
        @DisplayName("should return empty when no snapshots exist")
        void shouldReturnEmptyWhenNoSnapshotsFound() {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            Optional<ContextSnapshot> result = contextService.restoreState("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when vector store throws exception")
        void shouldReturnEmptyOnSearchFailure() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("ChromaDB unavailable"));

            Optional<ContextSnapshot> result = contextService.restoreState("test-project");

            assertThat(result).isEmpty();
        }
    }

    // ── List Tests ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listSnapshots")
    class ListSnapshotsTests {

        @Test
        @DisplayName("should return snapshots sorted newest-first")
        void shouldReturnSnapshotsSortedByTimestamp() throws Exception {
            String json1 = "{\"s\":\"s1\"}";
            String json2 = "{\"s\":\"s2\"}";

            ContextSnapshot older = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
                    .projectName("proj").sessionId("s1").build();
            ContextSnapshot newer = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2024-01-01T12:00:00Z"))
                    .projectName("proj").sessionId("s2").build();

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(new Document(json1), new Document(json2)));
            when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(older);
            when(objectMapper.readValue(json2, ContextSnapshot.class)).thenReturn(newer);

            List<ContextSnapshot> results = contextService.listSnapshots("proj");

            assertThat(results).hasSize(2);
            assertThat(results.getFirst().sessionId()).isEqualTo("s2");
            assertThat(results.getLast().sessionId()).isEqualTo("s1");
        }
    }

    // ── Analytics Tests ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProjectStats")
    class GetProjectStatsTests {

        @Test
        @DisplayName("should aggregate statistics per project correctly")
        void shouldAggregateCorrectly() throws Exception {
            String json1 = "{\"s\":\"s1\"}";
            String json2 = "{\"s\":\"s2\"}";

            ContextSnapshot completed = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
                    .projectName("projA").sessionId("s1")
                    .currentGoal("Goal 1").progressStatus("completed").build();
            ContextSnapshot inProgress = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2024-01-01T12:00:00Z"))
                    .projectName("projA").sessionId("s2")
                    .currentGoal("Goal 2").progressStatus("in_progress").build();

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(new Document(json1), new Document(json2)));
            when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(completed);
            when(objectMapper.readValue(json2, ContextSnapshot.class)).thenReturn(inProgress);

            List<Map<String, Object>> stats = contextService.getProjectStats();

            assertThat(stats).hasSize(1);
            Map<String, Object> projStats = stats.getFirst();
            assertThat(projStats)
                    .containsEntry("project_name", "projA")
                    .containsEntry("total_sessions", 2)
                    .containsEntry("completed", 1L)
                    .containsEntry("in_progress", 1L)
                    .containsEntry("latest_goal", "Goal 2");
        }
    }

    // ── Decision Search Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("searchDecisions")
    class SearchDecisionsTests {

        @Test
        @DisplayName("should return decisions matching the query")
        void shouldReturnMatchingDecisions() throws Exception {
            String json1 = "{\"s\":\"s1\"}";
            ContextSnapshot snapshot = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
                    .projectName("projA").sessionId("s1").currentGoal("Goal")
                    .progressStatus("in_progress")
                    .keyDecisionsLog(List.of(
                            new ContextSnapshot.KeyDecision("Use Postgres", "Better for relational data", "MySQL")))
                    .build();

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(new Document(json1)));
            when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(snapshot);

            List<Map<String, Object>> matches = contextService.searchDecisions("postgres");

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst())
                    .containsEntry("project_name", "projA")
                    .containsEntry("decision", "Use Postgres");
        }

        @Test
        @DisplayName("should return empty list when no decisions match")
        void shouldReturnEmptyWhenNoDecisionsMatch() throws Exception {
            String json1 = "{\"s\":\"s1\"}";
            ContextSnapshot snapshot = ContextSnapshot.builder()
                    .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
                    .projectName("projA").sessionId("s1")
                    .keyDecisionsLog(List.of(
                            new ContextSnapshot.KeyDecision("Use Redis", "For caching", "Memcached")))
                    .build();

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(new Document(json1)));
            when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(snapshot);

            List<Map<String, Object>> matches = contextService.searchDecisions("postgres");

            assertThat(matches).isEmpty();
        }
    }
}
