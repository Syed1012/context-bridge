package com.contextbridge.controller;

import com.contextbridge.model.ContextSnapshot;
import com.contextbridge.service.ContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(McpSseController.class)
class McpSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContextService contextService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startSseSession_ShouldReturnSseStream() throws Exception {
        mockMvc.perform(get("/mcp/sse"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
