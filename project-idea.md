# Architectural Blueprint: ContextBridge (Distributed AI Memory Plane)

## 1. System Vision & Architecture
ContextBridge is a local-first, distributed state-management system designed for AI coding assistants. It resolves LLM "amnesia" across isolated IDE sessions by decoupling session memory from the chat window. 

The system operates as a headless Model Context Protocol (MCP) server. It ingests structured "Context Snapshots" from IDE agents, computes semantic embeddings using a local LLM, and persists them in a vector database. It utilizes Server-Sent Events (SSE) to allow remote tunneling, enabling distributed agents to query centralized architectural state seamlessly.

## 2. Infrastructure & Tech Stack (Zero-Cost Local Setup)
* **Backend Orchestrator:** Java 21+, Spring Boot 3.2+
* **AI/Vector Middleware:** Spring AI (Ollama & Chroma DB integrations)
* **Vector Database:** Chroma DB (Dockerized, persistent volumes)
* **Local Inference Engine:** Ollama (Dockerized, model: `qwen2.5-coder:7b` or `llama3.2`)
* **Frontend Visualizer:** Next.js (App Router), React, TypeScript, Tailwind CSS, shadcn/ui.
* **Communication Protocol:** MCP via Server-Sent Events (SSE) for IDE integration.

## 3. Core Data Schema: The Context Snapshot
Raw chat logs are strictly prohibited. The MCP tools must enforce the ingestion and retrieval of structured state.

```json
{
  "timestamp": "ISO-8601",
  "project_name": "string",
  "session_id": "string",
  "current_goal": "Detailed description of the active task",
  "active_files": ["List of critical file paths"],
  "architectural_decisions": "Why specific patterns/tools were chosen during this session",
  "unresolved_issues": "Bugs or pending tasks for the next session"
}
```

## 4. MCP Tool Definitions (Exposed via SSE)

The Spring Boot backend must expose the following tools to the client IDE via the MCP SSE endpoint (e.g., `/mcp/sse`):

### `checkpoint_state`

- **Description:** Forces the LLM to summarize its current working context into the Context Snapshot JSON schema and sends it to the server.
- **Action:** Spring Boot receives the JSON, uses Spring AI to generate a text embedding via Ollama, and stores the document + embedding in Chroma DB.

### `restore_state`

- **Description:** Queries the server for the most recent or semantically relevant Context Snapshot for a given project.
- **Action:** Spring Boot queries Chroma DB, retrieves the dense snapshot, and returns it to the IDE agent to be injected into its system prompt.

## 5. Execution Strategy (Strict Phase-Gate Approach)

> **AI Agent Instructions:** You are executing this build as a Principal Systems Architect. Complete one phase entirely before proceeding to the next. Validate functionality at every gate.

### Phase 1: Containerized Infrastructure

1. Create a root `docker-compose.yml`.
2. Configure the Chroma DB service (expose port `8000`, map local volumes for persistence).
3. Configure the Ollama service (expose port `11434`, map local volumes, include an initialization command to pull the target model).

**Gate:** `docker compose up -d` must successfully start and persist both services.

### Phase 2: Backend Orchestration (Spring Boot)

1. Initialize the Spring Boot project in the root.
2. Configure `application.yml` to connect to local Chroma DB and Ollama instances.
3. Implement the `ContextService` to handle the serialization of the Context Snapshot and the embedding process via Spring AI.
4. Implement the MCP SSE Controller (`/mcp/sse`) exposing the `checkpoint_state` and `restore_state` tools.

**Gate:** The backend must compile, connect to the Docker containers, and successfully expose the SSE endpoint.

### Phase 3: The Memory Visualizer (Next.js)

1. Initialize the frontend app in `./frontend`.
2. Build a dark-mode dashboard using `shadcn/ui`.
3. Create a polling- or SSE-driven view that fetches and displays the stored Context Snapshots from Chroma DB (via a standard REST endpoint on the Spring Boot backend, separate from the MCP endpoint).

**Gate:** The dashboard must render locally and display mock/real data from the backend.