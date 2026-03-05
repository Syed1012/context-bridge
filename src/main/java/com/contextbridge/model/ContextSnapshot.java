package com.contextbridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Core data schema for a single context checkpoint.
 * Mirrors the JSON schema defined in project-idea.md.
 * <p>
 * Raw chat logs are strictly prohibited — only structured state is stored.
 */
@Builder
public record ContextSnapshot(

        /** ISO-8601 timestamp of when the snapshot was taken. */
        Instant timestamp,

        /** Logical name of the project this snapshot belongs to. */
        @JsonProperty("project_name")
        String projectName,

        /** Unique session identifier (e.g. IDE session UUID). */
        @JsonProperty("session_id")
        String sessionId,

        /**
         * Detailed description of the active task at the time of the snapshot.
         * This is the most important field — it should answer "what was I doing?"
         */
        @JsonProperty("current_goal")
        String currentGoal,

        /** Critical file paths that were open or modified during this session. */
        @JsonProperty("active_files")
        List<String> activeFiles,

        /**
         * Why specific architectural patterns, libraries, or tools were chosen.
         * Think of this as an inline ADR (Architecture Decision Record).
         */
        @JsonProperty("architectural_decisions")
        String architecturalDecisions,

        /**
         * Bugs, TODOs, or pending tasks that must be addressed in the next session.
         * Acts as a handoff note to the next AI (or human) session.
         */
        @JsonProperty("unresolved_issues")
        String unresolvedIssues
) {
    /** Convenience factory that auto-populates the timestamp. */
    public static ContextSnapshot withNow(
            String projectName,
            String sessionId,
            String currentGoal,
            List<String> activeFiles,
            String architecturalDecisions,
            String unresolvedIssues
    ) {
        return ContextSnapshot.builder()
                .timestamp(Instant.now())
                .projectName(projectName)
                .sessionId(sessionId)
                .currentGoal(currentGoal)
                .activeFiles(activeFiles)
                .architecturalDecisions(architecturalDecisions)
                .unresolvedIssues(unresolvedIssues)
                .build();
    }
}
