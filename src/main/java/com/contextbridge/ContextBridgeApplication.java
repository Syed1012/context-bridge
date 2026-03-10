package com.contextbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration;

@SpringBootApplication(exclude = { ChromaVectorStoreAutoConfiguration.class })
public class ContextBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextBridgeApplication.class, args);
    }
}
