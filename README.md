<p align="center">
  <h1 align="center">🧠 ContextBridge</h1>
  <p align="center">
    <strong>Your AI coding assistant forgets everything when you start a new chat.<br/>ContextBridge fixes that.</strong>
  </p>
  <p align="center">
    <a href="#quickstart">Quickstart</a> •
    <a href="#how-it-works">How It Works</a> •
    <a href="#the-schema">The Schema</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#contributing">Contributing</a>
  </p>
</p>

---

## The Problem

Every AI coding assistant — Copilot, Cursor, Cline, Gemini — suffers from the same fundamental issue: **amnesia**.

Switch to a new chat window? It forgets your architecture. Start a new session? It doesn't know what you were working on yesterday. Long conversation? It gradually loses track of what happened earlier.

You end up repeating yourself. Re-explaining decisions. Re-describing your codebase. Over and over.

**ContextBridge is the fix.**

## What It Does

ContextBridge is a local-first **MCP (Model Context Protocol) server** that acts as a persistent memory layer for your AI coding sessions.

It works like this:

1. **Your AI saves context** → At any point (end of session, mid-conversation, task switch), the AI calls `checkpoint_state` through MCP. It captures *everything*: what you were doing, what code changed, what decisions were made, what's left to do.

2. **Your AI restores context** → In a new chat, the AI calls `restore_state`. It gets back a rich, structured snapshot of your previous session — not a vague summary, but actionable detail it can immediately work with.

3. **Nothing is lost** → Your context is stored locally in [ChromaDB](https://www.trychroma.com/) as semantic embeddings. The most relevant snapshot is always retrievable, even across projects.

> Think of it as **Git for your AI's memory** — except instead of code, you're version-controlling the context that makes your AI actually useful.

## Quickstart

### Prerequisites

- Java 21+
- Docker (for ChromaDB)
- Node.js 18+ (for the dashboard)
- [Ollama](https://ollama.ai) running locally with an embedding model

### 1. Start the infrastructure

```bash
# Start ChromaDB
docker compose up -d

# Pull an embedding model (if you haven't)
ollama pull nomic-embed-text
```

### 2. Start the backend

```bash
# Set your Ollama config (defaults shown)
export OLLAMA_HOST=http://localhost:11434
export OLLAMA_EMBED_MODEL=nomic-embed-text
export CHROMA_HOST=localhost
export CHROMA_PORT=8000

# Run
./mvnw spring-boot:run
```

The MCP server will be live at `http://localhost:9090/mcp/sse`.

### 3. Start the dashboard (optional)

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000` — you'll see your saved snapshots visualized in real time.

### 4. Connect your AI

Point your AI coding assistant's MCP client at:

```
http://localhost:9090/mcp/sse
```

That's it. Your AI now has two new tools: `checkpoint_state` and `restore_state`.

## How It Works

```
┌─────────────────────┐         ┌──────────────────┐         ┌──────────┐
│  AI Coding Agent    │  MCP    │  ContextBridge    │  embed  │  Ollama  │
│  (Cursor/Copilot/   │◀──────▶│  (Spring Boot)    │◀──────▶│  (local) │
│   Cline/Gemini)     │  SSE   │  Port 9090        │         │  :11434  │
└─────────────────────┘         └────────┬─────────┘         └──────────┘
                                         │ store/query
                                         ▼
                                ┌──────────────────┐
                                │  ChromaDB         │
                                │  (vector store)   │
                                │  Port 8000        │
                                └──────────────────┘
```

**On `checkpoint_state`:**
1. AI structures its current context into the snapshot schema
2. Spring Boot receives the JSON via MCP
3. Ollama generates a semantic embedding
4. ChromaDB stores the document + embedding

**On `restore_state`:**
1. AI sends the project name
2. ChromaDB performs a semantic similarity search
3. The most relevant snapshot is returned as structured JSON
4. AI injects it into its working context

## The Schema

This is what makes ContextBridge different from "just saving chat logs." Every snapshot is **structured, actionable data** — not a raw conversation dump.

```json
{
  "timestamp": "2026-03-11T10:15:40Z",
  "project_name": "my-app",
  "session_id": "session-abc-123",
  "current_goal": "Implementing user authentication with OAuth2",
  "conversation_summary": "Set up Spring Security with Google OAuth2 provider. Had to downgrade spring-security-oauth2-client to 6.2.1 due to a redirect URI bug in 6.3.0. Login flow works, but logout redirect is broken — needs custom LogoutSuccessHandler.",
  "progress_status": "in_progress",
  "active_files": [
    "src/main/java/com/app/config/SecurityConfig.java",
    "src/main/resources/application.yml"
  ],
  "code_changes": [
    {
      "file": "src/main/java/com/app/config/SecurityConfig.java",
      "action": "created",
      "summary": "OAuth2 security configuration",
      "details": "Created SecurityFilterChain bean with OAuth2 login, configured Google as provider, added CSRF protection, set up role-based access for /api/** endpoints"
    }
  ],
  "key_decisions_log": [
    {
      "decision": "Downgraded spring-security-oauth2-client to 6.2.1",
      "rationale": "Version 6.3.0 has a known bug with redirect URIs containing query params",
      "alternatives_considered": "Patching the URI handler manually, waiting for 6.3.1"
    }
  ],
  "tech_stack": ["Spring Boot 3.5", "Java 21", "Spring Security 6.2.1"],
  "next_steps": [
    "Implement custom LogoutSuccessHandler for proper redirect",
    "Add JWT token generation for API authentication",
    "Write integration tests for the login flow"
  ]
}
```

Every field is designed to answer a specific question the next AI session will have:

| Field | Answers |
|-------|---------|
| `current_goal` | What was I working on? |
| `conversation_summary` | What happened? What worked? What didn't? |
| `progress_status` | Is this done or do I need to continue? |
| `code_changes` | What code did I write and why? |
| `key_decisions_log` | Why did I make these choices? |
| `next_steps` | What should I do first? |
| `tech_stack` | What am I building with? |

## Architecture

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Backend** | Spring Boot 3.5, Java 21 | MCP server, REST API, orchestration |
| **AI Middleware** | Spring AI | Embedding generation, vector store abstraction |
| **Vector Database** | ChromaDB | Semantic storage and similarity search |
| **Embedding Engine** | Ollama (local) | Generates text embeddings — zero API costs |
| **Frontend** | Next.js 16, React 19, Tailwind CSS | Dashboard for visualizing snapshots |
| **Communication** | MCP over SSE | IDE agent integration |

### Why Local-First?

- **Zero cost** — No API keys, no cloud bills, no token limits
- **Privacy** — Your code context never leaves your machine
- **Speed** — No network latency for embeddings
- **Offline** — Works without internet

## Project Structure

```
context-bridge/
├── src/main/java/com/contextbridge/
│   ├── model/          # ContextSnapshot schema
│   ├── service/        # Core checkpoint/restore logic
│   ├── controller/     # MCP SSE + REST endpoints
│   └── config/         # ChromaDB, CORS configuration
├── frontend/           # Next.js dashboard
├── docker-compose.yml  # ChromaDB container
└── pom.xml            # Spring Boot + Spring AI dependencies
```

## Who Is This For?

- **Developers using AI coding assistants** who are tired of repeating context
- **Teams** where multiple people (or AIs) work on the same codebase across sessions
- **Anyone** who's ever started a new AI chat and thought *"I wish it remembered what we did yesterday"*

## License

This project is licensed under the [MIT License](LICENSE) — use it, fork it, make your AI remember things.