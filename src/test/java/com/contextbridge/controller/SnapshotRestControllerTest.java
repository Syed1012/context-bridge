package com.contextbridge.controller;

import com.contextbridge.model.ContextSnapshot;
import com.contextbridge.service.ContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SnapshotRestController.class)
class SnapshotRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContextService contextService;

    @Test
    void getSnapshots_ShouldReturnList() throws Exception {
        ContextSnapshot snapshot = ContextSnapshot.builder()
                .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
                .projectName("project-a")
                .sessionId("s1")
                .currentGoal("goal")
                .conversationSummary("summary")
                .progressStatus("in_progress")
                .build();

        when(contextService.listSnapshots(anyString())).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/snapshots")
                        .param("project", "project-a")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].project_name").value("project-a"))
                .andExpect(jsonPath("$[0].session_id").value("s1"));
    }
}
