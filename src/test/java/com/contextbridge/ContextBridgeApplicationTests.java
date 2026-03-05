package com.contextbridge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContextBridgeApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context assembles without errors.
    }
}
