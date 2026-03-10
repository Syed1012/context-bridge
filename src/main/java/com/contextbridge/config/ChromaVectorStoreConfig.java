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

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(chromaUrl)
                .defaultStatusHandler(
                        status -> status.is4xxClientError(),
                        (request, response) -> {
                            if (response.getStatusCode().value() == 400 || response.getStatusCode().value() == 404) {
                                log.warn(
                                        "Chroma returned {} - Collection might not exist yet, ignoring to prevent crash",
                                        response.getStatusCode());
                                // Do not throw an exception intentionally to let Spring AI proceed
                            } else {
                                throw HttpClientErrorException.create(response.getStatusCode(),
                                        response.getStatusText(), response.getHeaders(), null, null);
                            }
                        });

        ChromaApi chromaApi = new ChromaApi(chromaUrl, restClientBuilder, objectMapper);

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true) // Spring AI creates the collection on startup
                .build();
    }
}
