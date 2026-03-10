# ContextBridge — Testing & Usage Guide

## How It Actually Works (Read This First)

ContextBridge is **NOT automatic**. It does **NOT** silently save your chat in the background.  
It gives your AI two **tools** — and **you tell the AI when to use them**.

| Tool                | What It Does                                          | When To Use It                        |
| ------------------- | ----------------------------------------------------- | ------------------------------------- |
| `checkpoint_state`  | Saves a structured summary of your current session    | Before closing a chat / switching tasks |
| `restore_state`     | Retrieves the last saved context for a project        | At the start of a new chat session    |

Think of it like a **manual save/load in a video game** — you decide when to save, and you decide when to load.

No `@` symbol needed. No slash commands. You just **talk to the AI in plain English**.

---

## Part 1 — Prerequisites (Server Machine)

Your server machine (`10.10.3.126`) must be running these three things:

| Service     | Port   | How To Start                                                      |
| ----------- | ------ | ----------------------------------------------------------------- |
| ChromaDB    | `8000` | `docker compose up -d` (from the `context-bridge/` project root) |
| Ollama      | `11434`| Should already be running with `nomic-embed-text` model pulled    |
| Spring Boot | `9090` | `./mvnw spring-boot:run` (from the `context-bridge/` project root)|

### Quick Health Checks (run from any machine)

```bash
# ChromaDB alive?
curl http://10.10.3.126:8000/api/v2/heartbeat

# Spring Boot alive?
curl http://10.10.3.126:9090/actuator/health

# Ollama alive?
curl http://10.10.3.126:11434/api/tags
```

All three must respond before you proceed.

---

## Part 2 — Setting Up The Client Machine (Your Laptop / Other Device)

### Step 1: Open any project in VS Code

This can be **any project** — it doesn't have to be the context-bridge project itself.  
Open whatever project you want to work on (e.g., a React app, a Python project, anything).

### Step 2: Create the MCP config file

In **that project's root**, create the file `.vscode/mcp.json`:

```jsonc
{
  "servers": {
    "context-bridge": {
      "type": "sse",
      "url": "http://10.10.3.126:9090/mcp/sse"
    }
  }
}
```

> **⚠️ Change the IP** if your server is at a different address.  
> The port `9090` is the Spring Boot backend, NOT ChromaDB.

### Step 3: Reload VS Code

After creating the file, either:
- Press `Cmd+Shift+P` → type **"Developer: Reload Window"** → Enter
- Or just close and reopen VS Code

### Step 4: Verify the MCP server is connected

1. Open Copilot Chat (the chat panel)
2. Look for the **tools icon** (🔧) at the bottom of the chat input box
3. Click it — you should see `checkpoint_state` and `restore_state` listed under **"context-bridge"**

If you see them, you're connected. If not, check:
- Is the backend running on the server?
- Can your machine reach `10.10.3.126:9090`? (try `curl` or `ping`)
- Is the `.vscode/mcp.json` file in the right place?

---

## Part 3 — How To Save Context (checkpoint_state)

### When you're done working on something, just tell the AI:

> **You say:**  
> "Save the current context of this session to context-bridge for project my-cool-app"

Or more naturally:

> "Hey, checkpoint the current state. Project name is my-cool-app."

Or even:

> "Before I close this chat, save everything we've been working on. The project is my-cool-app and we were fixing the login bug."

### What happens behind the scenes:

1. The AI calls the `checkpoint_state` MCP tool
2. It fills in the fields: project name, session ID, current goal, active files, decisions made, unresolved issues
3. The Spring Boot backend receives the snapshot
4. Ollama generates an embedding of the snapshot
5. The snapshot + embedding are stored in ChromaDB
6. You get a confirmation back

### Example conversation:

```
You:    "We just finished setting up the authentication flow using JWT tokens.
         The main files were auth.ts, middleware.ts, and login.tsx.
         We still need to add refresh token logic.
         Save this context for project 'ecommerce-app'."

AI:     "I've saved the context snapshot for project 'ecommerce-app'. 
         Doc ID: e2847655-4e92-4a80-acb6-5c56f57f41e5"
```

That's it. No commands, no `@` symbols, no special syntax.

---

## Part 4 — How To Restore Context (restore_state)

### When you open a new chat window (next day, different machine, etc.):

> **You say:**  
> "Restore the context for project my-cool-app from context-bridge"

Or:

> "What was I working on last time for project my-cool-app? Load the saved context."

Or simply:

> "Get my last session context for ecommerce-app"

### What happens behind the scenes:

1. The AI calls the `restore_state` MCP tool with the project name
2. Spring Boot runs a semantic similarity search in ChromaDB
3. The most relevant snapshot is returned
4. The AI reads it and now knows: what you were doing, which files matter, what decisions were made, and what's still unresolved

### Example conversation:

```
You:    "Restore context for project ecommerce-app"

AI:     "Here's your last session context:

         Project: ecommerce-app
         Goal: Setting up JWT authentication flow
         Active files: auth.ts, middleware.ts, login.tsx
         Decisions: Using JWT with httpOnly cookies for security
         Unresolved: Refresh token rotation logic not yet implemented

         Would you like to continue working on the refresh token logic?"
```

Now the AI has full context and you can continue exactly where you left off — even though it's a brand new chat window.

---

## Part 5 — Testing It End-to-End

Here's a step-by-step test you can do right now on your other machine:

### Test 1: Save context

1. Open **any project** in VS Code on the other machine
2. Make sure `.vscode/mcp.json` exists (see Part 2)
3. Open Copilot Chat
4. Type:

   > "Save a test context snapshot to context-bridge. Project name: test-project. Session: test-001. Current goal: Testing the context bridge system. Active files: guide.md. No architectural decisions or unresolved issues."

5. The AI should confirm the snapshot was saved

### Test 2: Verify it was saved (via the dashboard)

Open a browser and go to:

```
http://10.10.3.126:3000
```

You should see the snapshot card for `test-project` on the frontend dashboard.

Or verify via curl:

```bash
curl http://10.10.3.126:9090/api/snapshots
```

### Test 3: Restore context in a new chat

1. **Close the current chat** (click the `+` to start a new chat, or close and reopen)
2. In the fresh chat, type:

   > "Restore context for project test-project from context-bridge"

3. The AI should return the snapshot you saved in Test 1

### Test 4: Cross-machine test

1. Go to a **completely different machine** on the same network
2. Open any project in VS Code
3. Create `.vscode/mcp.json` pointing to `http://10.10.3.126:9090/mcp/sse`
4. Open Copilot Chat and say:

   > "Restore context for test-project"

5. It should return the same snapshot — proving context is shared across machines

---

## Part 6 — Common Questions

### "Do I need to type `@context-bridge` or something?"
**No.** The MCP tools are automatically available to the AI once the server is connected. Just talk naturally. The AI will decide to use the tools based on what you ask.

### "Will it save my chat automatically?"
**No.** You must explicitly ask the AI to save the context. This is by design — you control what gets saved, and only structured summaries are stored (never raw chat logs).

### "What if I forget to save before closing?"
The context is lost for that session. Always save before closing a chat if you want to resume later.

### "Can I save multiple times in one session?"
**Yes.** Each save creates a new snapshot. The restore will return the most semantically relevant one.

### "Does the project I'm working in need to be the context-bridge repo?"
**No.** You can be in **any** project. The `.vscode/mcp.json` file just tells VS Code where the MCP server lives. The project name you give to `checkpoint_state` is just a label — it doesn't have to match your folder name.

### "What if the AI doesn't use the tool?"
If the AI doesn't seem to call the tool, be more explicit:

> "Use the checkpoint_state tool to save context for project X"

Or check that the tools are visible (🔧 icon in chat input).

### "Can I use this from the terminal / curl?"
**Yes.** The REST endpoints still work:

```bash
# Save a snapshot
curl -X POST http://10.10.3.126:9090/mcp/checkpoint_state \
  -H "Content-Type: application/json" \
  -d '{
    "project_name": "test-project",
    "session_id": "manual-001",
    "current_goal": "Testing via curl",
    "timestamp": "2026-03-10T15:00:00Z",
    "active_files": ["guide.md"],
    "architectural_decisions": null,
    "unresolved_issues": null
  }'

# Restore a snapshot
curl -X POST http://10.10.3.126:9090/mcp/restore_state \
  -H "Content-Type: application/json" \
  -d '{"project_name": "test-project"}'

# List all snapshots (used by the frontend dashboard)
curl http://10.10.3.126:9090/api/snapshots
```

---

## Quick Reference Card

```
┌──────────────────────────────────────────────────────────────┐
│                    ContextBridge Cheat Sheet                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  TO SAVE:    "Save context for project <name>"               │
│  TO LOAD:    "Restore context for project <name>"            │
│                                                              │
│  CONFIG:     .vscode/mcp.json in your project root           │
│  DASHBOARD:  http://<server-ip>:3000                         │
│  BACKEND:    http://<server-ip>:9090                         │
│                                                              │
│  No @ symbols.  No slash commands.  Just plain English.      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```
