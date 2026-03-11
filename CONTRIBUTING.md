# Contributing to ContextBridge

First off — thank you for considering contributing! Whether it's a bug fix, a feature, or just improving docs, every contribution makes ContextBridge better for everyone.

## Getting Started

### 1. Fork & Clone

```bash
git clone https://github.com/YOUR_USERNAME/context-bridge.git
cd context-bridge
```

### 2. Set Up the Development Environment

**Prerequisites:**
- Java 21+
- Docker (for ChromaDB)
- Node.js 18+ (for the frontend)
- [Ollama](https://ollama.ai) with an embedding model (`nomic-embed-text`)

**Start infrastructure:**
```bash
docker compose up -d
ollama pull nomic-embed-text
```

**Run the backend:**
```bash
./mvnw spring-boot:run
```

**Run the frontend:**
```bash
cd frontend
npm install
npm run dev
```

### 3. Verify Everything Works

- Backend: `http://localhost:9090/actuator/health` should return `UP`
- Frontend: `http://localhost:3000` should load the dashboard
- MCP: `http://localhost:9090/mcp/sse` should accept SSE connections

## How to Contribute

### Reporting Bugs

Open an issue with:
- **What happened** — describe the unexpected behavior
- **What you expected** — describe what should have happened
- **Steps to reproduce** — how can we recreate the issue?
- **Environment** — OS, Java version, Docker version, which AI assistant you're using

### Suggesting Features

Open an issue tagged `enhancement` with:
- **The problem** — what pain point does this solve?
- **Your proposed solution** — how would you approach it?
- **Alternatives you've considered** — what else could work?

### Submitting Code

1. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** — follow the code style of the existing codebase

3. **Test your changes:**
   ```bash
   # Backend
   ./mvnw compile    # must pass
   ./mvnw test       # if applicable
   
   # Frontend
   cd frontend
   npm run build     # must pass
   ```

4. **Commit with a clear message:**
   ```
   feat: add snapshot filtering by progress status
   ```
   
   We loosely follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` — new feature
   - `fix:` — bug fix
   - `docs:` — documentation changes
   - `refactor:` — code restructuring without behavior change
   - `chore:` — build, CI, or tooling changes

5. **Open a Pull Request** against `main` with a clear description of what and why.

## Code Style

### Java (Backend)
- **Logging** — Use `[Component]` prefixes (e.g., `[Chroma]`, `[MCP]`, `[Snapshot]`)
- **Records** — Prefer Java records for data classes
- **Builder pattern** — Use Lombok `@Builder` for records with many fields
- **Error handling** — Log actionable error messages that tell the developer what to check

### TypeScript (Frontend)
- Follow the existing Next.js App Router patterns
- Use shadcn/ui components where possible
- Tailwind CSS for styling

## Architecture Overview

If you're new to the codebase, here's the quick map:

| Layer | Files | What It Does |
|-------|-------|-------------|
| **Model** | `ContextSnapshot.java` | The data schema — a Java record with 13 fields |
| **Service** | `ContextService.java` | Core logic: serialize → embed → store (and reverse for restore) |
| **MCP Controller** | `McpSseController.java` | SSE endpoint + JSON-RPC handler for MCP protocol |
| **REST Controller** | `SnapshotRestController.java` | Simple REST endpoints for the dashboard |
| **Config** | `ChromaVectorStoreConfig.java` | ChromaDB connection and collection setup |
| **Frontend** | `frontend/` | Next.js dashboard showing stored snapshots |

## Questions?

Open an issue or start a discussion — there are no silly questions.
