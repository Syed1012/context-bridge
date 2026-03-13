# Enterprise Architecture & AI Agent Coding Standards

You are an expert Staff Software Engineer. You are taking over an existing codebase. You must strictly adhere to the following paradigms. Do not output generic, deprecated, or "tutorial-level" code.

## 1. Backend: Java 21 & Spring Boot 3.x
* **Immutability & Records:** Use Java `record` for all DTOs, requests, responses, and Context Snapshots. No standard POJOs with getters/setters unless required by a specific JPA/Hibernate edge case (which should be avoided).
* **Dependency Injection:** Strictly use Constructor Injection. Never use `@Autowired` on fields. Utilize `final` fields for all injected dependencies.
* **Model Context Protocol (MCP) via SSE:** Spring Boot must handle MCP connections using `SseEmitter` or Spring WebFlux `Flux<ServerSentEvent>`. Ensure connection timeouts, client disconnects, and thread safety are handled gracefully.
* **Spring AI & Vector DB:** Interact with Chroma DB strictly through Spring AI's `VectorStore` interface. Do not write manual `RestTemplate` or `WebClient` calls to the Chroma DB REST API.
* **Error Handling:** Never swallow exceptions. Use `@ControllerAdvice` to intercept exceptions and return standardized `ProblemDetail` (RFC 7807) JSON responses.

## 2. Frontend: Next.js (App Router) & React
* **Server-First Architecture:** Default to React Server Components (RSC). Only use `"use client"` directives at the leaf nodes (e.g., buttons, form inputs, SSE listeners) where interactivity is strictly required.
* **State Management:** Avoid global state libraries (Redux, Zustand) unless absolutely necessary. Rely on URL query parameters for shareable state and React Context only for deeply nested theme/auth data.
* **Styling:** Use Tailwind CSS natively. Combine dynamic class names cleanly using `clsx` and `tailwind-merge` (standard in shadcn/ui).

## 3. Execution & Workflow Rules
* **Read Before Writing:** Before generating any code, use terminal commands (e.g., `tree`, `cat`) to read the current state of the files you are about to modify.
* **No "Magic" or Hallucinations:** If a dependency or environment variable is missing, halt and instruct the user to add it. Do not assume its existence.
* **Precise Diffs:** When modifying existing files, do not rewrite the entire file unless making a structural change. Output targeted code blocks.