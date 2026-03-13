package com.contextbridge.service;

import com.contextbridge.model.ContextSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
        sampleSnapshot = new ContextSnapshot(
                Instant.now(),
                "test-project",
                "session-123",
                "Testing current goal",
                List.of("src/file1.txt"),
                null,
                null,
                "Testing summary",
                List.of("Java"),
                List.of(),
                List.of(new ContextSnapshot.KeyDecision("Test decision", "Test rationale", "Test alternatives")),
                "in_progress",
                List.of("Next step"),
                List.of()
        );
    }

    @Test
    void checkpointState_ShouldSaveSnapshotWithMetadata() throws Exception {
        // Arrange
        String json = "{\"projectName\": \"test-project\"}";
        when(objectMapper.writeValueAsString(any(ContextSnapshot.class))).thenReturn(json);

        // Act
        String docId = contextService.checkpointState(sampleSnapshot);

        // Assert
        verify(vectorStore, times(1)).add(documentListCaptor.capture());
        List<Document> savedDocs = documentListCaptor.getValue();
        assertThat(savedDocs).hasSize(1);

        Document savedDoc = savedDocs.get(0);
        assertThat(docId).isEqualTo(savedDoc.getId());
        assertThat(savedDoc.getText()).isEqualTo(json);

        Map<String, Object> metadata = savedDoc.getMetadata();
        assertThat(metadata).containsEntry("project_name", "test-project");
        assertThat(metadata).containsEntry("session_id", "session-123");
        assertThat(metadata).containsEntry("progress_status", "in_progress");
    }

    @Test
    void restoreState_ShouldReturnMostRecentSnapshot() throws Exception {
        // Arrange
        String json1 = "{\"projectName\": \"test-project\", \"timestamp\": \"2023-01-01T10:00:00Z\"}";
        String json2 = "{\"projectName\": \"test-project\", \"timestamp\": \"2023-01-01T12:00:00Z\"}"; // Newer

        Document doc1 = new Document(json1);
        Document doc2 = new Document(json2);

        ContextSnapshot snapshot1 = new ContextSnapshot(Instant.parse("2023-01-01T10:00:00Z"), "test-project", "s1", null, null, null, null, null, null, null, null, null, null, null);
        ContextSnapshot snapshot2 = new ContextSnapshot(Instant.parse("2023-01-01T12:00:00Z"), "test-project", "s2", null, null, null, null, null, null, null, null, null, null, null);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));
        when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(snapshot1);
        when(objectMapper.readValue(json2, ContextSnapshot.class)).thenReturn(snapshot2);

        // Act
        Optional<ContextSnapshot> result = contextService.restoreState("test-project");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().sessionId()).isEqualTo("s2"); // Should pick the newer one
    }

    @Test
    void restoreState_WhenNoSnapshotsFound_ShouldReturnEmpty() {
        // Arrange
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // Act
        Optional<ContextSnapshot> result = contextService.restoreState("test-project");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void listSnapshots_ShouldReturnAllSnapshotsSorted() throws Exception {
        // Arrange
        String json1 = "{\"sessionId\": \"s1\"}";
        ContextSnapshot snapshot1 = new ContextSnapshot(Instant.parse("2024-01-01T10:00:00Z"), "proj", "s1", null, null, null, null, null, null, null, null, null, null, null);

        String json2 = "{\"sessionId\": \"s2\"}";
        ContextSnapshot snapshot2 = new ContextSnapshot(Instant.parse("2024-01-01T12:00:00Z"), "proj", "s2", null, null, null, null, null, null, null, null, null, null, null);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document(json1), new Document(json2)));
        
        when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(snapshot1);
        when(objectMapper.readValue(json2, ContextSnapshot.class)).thenReturn(snapshot2);

        // Act
        List<ContextSnapshot> results = contextService.listSnapshots("proj");

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results.get(0).sessionId()).isEqualTo("s2"); // Newer first
        assertThat(results.get(1).sessionId()).isEqualTo("s1");
    }

    @Test
    void getProjectStats_ShouldAggregateCorrectly() throws Exception {
        // Arrange
        String json1 = "{\"sessionId\": \"s1\"}";
        ContextSnapshot snapshot1 = new ContextSnapshot(Instant.parse("2024-01-01T10:00:00Z"), "projA", "s1", "Goal 1", null, null, null, null, null, null, null, "completed", null, null);

        String json2 = "{\"sessionId\": \"s2\"}";
        ContextSnapshot snapshot2 = new ContextSnapshot(Instant.parse("2024-01-01T12:00:00Z"), "projA", "s2", "Goal 2", null, null, null, null, null, null, null, "in_progress", null, null);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document(json1), new Document(json2)));
        
        when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(snapshot1);
        when(objectMapper.readValue(json2, ContextSnapshot.class)).thenReturn(snapshot2);

        // Act
        List<Map<String, Object>> stats = contextService.getProjectStats();

        // Assert
        assertThat(stats).hasSize(1);
        Map<String, Object> projStats = stats.get(0);
        assertThat(projStats.get("project_name")).isEqualTo("projA");
        assertThat(projStats.get("total_sessions")).isEqualTo(2);
        assertThat(projStats.get("completed")).isEqualTo(1L);
        assertThat(projStats.get("in_progress")).isEqualTo(1L);
        assertThat(projStats.get("latest_goal")).isEqualTo("Goal 2");
    }

    @Test
    void searchDecisions_ShouldReturnMatches() throws Exception {
        // Arrange
        String json1 = "{\"sessionId\": \"s1\"}";
        ContextSnapshot snapshotWithDecision = new ContextSnapshot(
                Instant.parse("2024-01-01T10:00:00Z"), "projA", "s1", "Goal", null, null, null, null, null, null,
                List.of(new ContextSnapshot.KeyDecision("Use Postgres", "Fits relational data better", "MySQL")),
                "status", null, null
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document(json1)));
        
        when(objectMapper.readValue(json1, ContextSnapshot.class)).thenReturn(snapshotWithDecision);

        // Act
        List<Map<String, Object>> matches = contextService.searchDecisions("postgres");

        // Assert
        assertThat(matches).hasSize(1);
        Map<String, Object> match = matches.get(0);
        assertThat(match.get("project_name")).isEqualTo("projA");
        assertThat(match.get("decision")).isEqualTo("Use Postgres");
    }
}
