package com.contextbridge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContextBridgeApplicationTests {

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
        // Verifies the Spring context assembles without errors.
    }
}
