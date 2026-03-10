package com.contextbridge.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Custom ChromaDB config that handles Chroma 0.5.x API compatibility.
 * <p>
 * Spring AI 1.0.0's {@code initializeSchema(true)} doesn't work with Chroma
 * 0.5.20,
 * so we manually ensure the collection exists before building the VectorStore.
 */
@Slf4j
@Configuration
public class ChromaVectorStoreConfig {

    @Value("${spring.ai.vectorstore.chroma.host:localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.port:8000}")
    private String chromaPort;

    @Value("${spring.ai.vectorstore.chroma.collection-name:contextsnapshots}")
    private String collectionName;

    @Bean
    @ConditionalOnMissingBean
    public ChromaVectorStore vectorStore(EmbeddingModel embeddingModel,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        String chromaUrl = "http://" + chromaHost + ":" + chromaPort;
        log.info("Connecting to ChromaDB at {} with collection '{}'", chromaUrl, collectionName);

        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(chromaUrl);

        // Ensure collection exists before Spring AI tries to use it.
        // Spring AI 1.0.0's initializeSchema doesn't work with Chroma 0.5.x,
        // so we create the collection directly via the Chroma REST API.
        ensureCollectionExists(chromaUrl, collectionName);

        ChromaApi chromaApi = new ChromaApi(chromaUrl, restClientBuilder, objectMapper);

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(false) // We handle creation ourselves above
                .build();
    }

    /**
     * Calls Chroma's POST /api/v1/collections to create the collection if it
     * doesn't exist.
     * Chroma returns 200 for new collections and ignores duplicates (get_or_create
     * behaviour
     * when metadata is not specified), so this is safe to call on every startup.
     */
    private void ensureCollectionExists(String chromaUrl, String collectionName) {
        try {
            RestClient client = RestClient.builder().baseUrl(chromaUrl).build();
            String body = "{\"name\":\"" + collectionName + "\",\"get_or_create\":true}";

            client.post()
                    .uri("/api/v1/collections")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Chroma collection '{}' is ready.", collectionName);
        } catch (HttpClientErrorException e) {
            log.warn("Could not ensure Chroma collection exists ({}). " +
                    "It may already exist, proceeding.", e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to connect to ChromaDB at {}. Is it running?", chromaUrl, e);
            throw new IllegalStateException("ChromaDB is not reachable at " + chromaUrl, e);
        }
    }
}
