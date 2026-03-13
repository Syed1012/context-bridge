package com.contextbridge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ContextBridgeApplicationTests {

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
        // Verifies the Spring context assembles without errors.
    }
}
