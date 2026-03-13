package com.contextbridge.controller;

import com.contextbridge.service.ContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsRestController.class)
class AnalyticsRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContextService contextService;

    @Test
    void getStats_ShouldReturnList() throws Exception {
        when(contextService.getProjectStats()).thenReturn(List.of(
                Map.of("project_name", "project-a", "total_sessions", 5)
        ));

        mockMvc.perform(get("/api/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_projects").value(1))
                .andExpect(jsonPath("$.projects[0].project_name").value("project-a"))
                .andExpect(jsonPath("$.projects[0].total_sessions").value(5));
    }

    @Test
    void searchDecisions_ShouldReturnList() throws Exception {
        when(contextService.searchDecisions("postgres")).thenReturn(List.of(
                Map.of("project_name", "project-a", "decision", "Use Postgres")
        ));

        mockMvc.perform(get("/api/decisions")
                        .param("q", "postgres")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("postgres"))
                .andExpect(jsonPath("$.results_count").value(1))
                .andExpect(jsonPath("$.decisions[0].project_name").value("project-a"))
                .andExpect(jsonPath("$.decisions[0].decision").value("Use Postgres"));
    }
}
