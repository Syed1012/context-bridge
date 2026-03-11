package com.contextbridge.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Custom ChromaDB config that handles Chroma 0.5.x + Spring AI 1.0.0 compatibility.
 * <p>
 * Spring AI 1.0.0's {@code ChromaApi.getCollection()} expects a specific error
 * message format from ChromaDB when a collection is not found. Chroma 0.5.20's
 * messages don't match that format, so {@code initializeSchema(true)} fails.
 * <p>
 * We work around this by pre-creating the collection via the Chroma REST API
 * before building the VectorStore, and using Chroma's native tenant/database
 * ({@code default_tenant}/{@code default_database}) instead of Spring AI's
 * custom defaults ({@code SpringAiTenant}/{@code SpringAiDatabase}).
 */
@Slf4j
@Configuration
public class ChromaVectorStoreConfig {

    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    @Value("${spring.ai.vectorstore.chroma.host:localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.port:8000}")
    private String chromaPort;

    @Value("${spring.ai.vectorstore.chroma.collection-name:contextsnapshots}")
    private String collectionName;

    @Bean
    public ChromaApi chromaApi(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        String chromaUrl = "http://" + chromaHost + ":" + chromaPort;
        // Use a fresh RestClient.Builder — do NOT use the Spring Boot auto-configured
        // one, as ChromaApi's constructor will mutate it (add baseUrl and defaultHeaders).
        RestClient.Builder restClientBuilder = RestClient.builder();
        return new ChromaApi(chromaUrl, restClientBuilder, objectMapper);
    }

    @Bean
    public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        String chromaUrl = "http://" + chromaHost + ":" + chromaPort;
        log.info("[Chroma] Connecting to {} — collection '{}'", chromaUrl, collectionName);

        // Pre-create the collection using ChromaApi which handles JSON serialization
        // correctly. This avoids the error-message mismatch in getCollection().
        ensureCollectionExists(chromaApi);

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .tenantName(DEFAULT_TENANT)
                .databaseName(DEFAULT_DATABASE)
                .initializeSchema(false) // We've already created it above
                .build();
    }

    /**
     * Creates the collection if it doesn't exist, using ChromaApi's own methods
     * which properly serialize requests for the v2 API.
     */
    private void ensureCollectionExists(ChromaApi chromaApi) {
        try {
            // Try to get the collection first
            var collection = chromaApi.getCollection(DEFAULT_TENANT, DEFAULT_DATABASE, collectionName);
            if (collection != null) {
                log.info("[Chroma] Collection '{}' verified — ready", collectionName);
                return;
            }
        } catch (Exception e) {
            // getCollection throws RuntimeException if the error message doesn't match
            // the expected pattern (Chroma 0.5.x compat issue) — that's expected.
            log.debug("[Chroma] Collection lookup failed (expected on Chroma 0.5.x): {}", e.getMessage());
        }

        // Collection doesn't exist or couldn't be checked — try to create it
        try {
            var created = chromaApi.createCollection(DEFAULT_TENANT, DEFAULT_DATABASE,
                    new ChromaApi.CreateCollectionRequest(collectionName));
            if (created != null) {
                log.info("[Chroma] Collection '{}' created (id={})", collectionName, created.id());
            }
        } catch (Exception e) {
            // If creation fails with "already exists" that's fine (concurrent startup)
            log.warn("[Chroma] Could not create collection '{}': {}. If ChromaDB is running, it may already exist — safe to ignore.",
                    collectionName, e.getMessage());
        }
    }
}
