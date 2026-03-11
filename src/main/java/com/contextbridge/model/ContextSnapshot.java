package com.contextbridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Core data schema for a single context checkpoint.
 * <p>
 * Captures structured state from an AI coding session — goal, decisions,
 * code changes, progress, and a full conversation summary. Designed to give
 * the next AI session everything it needs to continue seamlessly.
 * <p>
 * Raw chat logs are strictly prohibited — only structured state is stored.
 */
@Builder
public record ContextSnapshot(

        // ── Core Fields (original) ──────────────────────────────────────────

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
         * Legacy field — prefer {@code keyDecisionsLog} for richer decision tracking.
         */
        @JsonProperty("architectural_decisions")
        String architecturalDecisions,

        /**
         * Bugs, TODOs, or pending tasks that must be addressed in the next session.
         * Legacy field — prefer {@code nextSteps} for a structured ordered handoff.
         */
        @JsonProperty("unresolved_issues")
        String unresolvedIssues,

        // ── Enriched Fields (v2) ────────────────────────────────────────────

        /**
         * Condensed summary of the entire session — what was discussed,
         * what was tried, what worked, what failed. This is the single most
         * valuable field for session continuity.
         */
        @JsonProperty("conversation_summary")
        String conversationSummary,

        /**
         * Key technologies and versions in use for this project.
         * Helps the next AI session understand the stack without guessing.
         * Example: ["Spring Boot 3.5", "Java 21", "ChromaDB 0.5", "Next.js 16"]
         */
        @JsonProperty("tech_stack")
        List<String> techStack,

        /**
         * Detailed list of code changes made during this session.
         * Each entry includes the file, action, summary, and specific details
         * of what was changed and why — enabling the next session to pick up
         * exactly where this one left off.
         */
        @JsonProperty("code_changes")
        List<CodeChange> codeChanges,

        /**
         * Structured decision log — each decision with rationale and
         * alternatives considered. Replaces the single-string
         * {@code architectural_decisions} with richer tracking.
         */
        @JsonProperty("key_decisions_log")
        List<KeyDecision> keyDecisionsLog,

        /**
         * Current progress state: completed, in_progress, blocked, or abandoned.
         * Enables filtering snapshots by status and helps the next session
         * understand whether to continue, debug, or start fresh.
         */
        @JsonProperty("progress_status")
        String progressStatus,

        /**
         * Explicit ordered list of what the next session should do first.
         * Think of this as a prioritized handoff checklist.
         */
        @JsonProperty("next_steps")
        List<String> nextSteps,

        /**
         * Doc IDs of prior snapshots this one builds on.
         * Enables session chaining — the AI can follow the thread of work
         * across multiple sessions.
         */
        @JsonProperty("related_snapshots")
        List<String> relatedSnapshots

) {

    // ── Inner Records ───────────────────────────────────────────────────

    /**
     * A single code change with full detail so the next session understands
     * exactly what was modified and why.
     */
    @Builder
    public record CodeChange(

            /** File path relative to project root. */
            String file,

            /** What happened: created, modified, deleted, or renamed. */
            String action,

            /** One-line summary of the change. */
            String summary,

            /**
             * Detailed description of what was changed — specific methods added,
             * logic modified, configurations changed, etc. This is what lets
             * the next AI session truly pick up where you left off.
             */
            String details
    ) {}

    /**
     * A single architectural or technical decision with rationale.
     */
    @Builder
    public record KeyDecision(

            /** What was decided. */
            String decision,

            /** Why this choice was made. */
            String rationale,

            /** What other options were considered. */
            @JsonProperty("alternatives_considered")
            String alternativesConsidered
    ) {}

    // ── Factory ─────────────────────────────────────────────────────────

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
