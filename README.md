# AI Project Reviewer

A Java application that automatically evaluates a software project (its source code) using
**deterministic static analysis** plus **real LLM calls**, then generates a **LaTeX evaluation
report**.

Pipeline: import a project → explore its file tree → pick evaluation criteria → run analyses →
consolidate scores → generate `.tex` / `.pdf` → keep a history of past runs.

M1 software-architecture project, team of 6. The grade is primarily about the **architecture**
(low coupling, justified design patterns, LLM interchangeability, resilience to LLM failure, safe
handling of untrusted analyzed code, extensibility) — not about feature count.

## Start here

| If you want... | Read |
|---|---|
| The rules any code (or AI assistant) in this repo must follow | [`CLAUDE.md`](CLAUDE.md) |
| Why the project is shaped this way: architecture, patterns, roles, timeline | [`docs/TEAM_BRIEF.md`](docs/TEAM_BRIEF.md) |
| To get a machine set up and make your first commit | [`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md) |

## Status

**Scaffold only.** Package directories, build, docs and the sandbox image exist; no business
logic yet. Each role implements its own package — see the package-status table in
[`CLAUDE.md`](CLAUDE.md#1-package-layout-current-status).

## Quick build

```bash
mvn compile      # compile
mvn test         # all tests — never needs Docker or a live LLM
mvn package      # target/ai-project-reviewer.jar (shaded, runnable once Main exists)
```

## Layout

```
CLAUDE.md                     enforceable rules, auto-loaded by Claude Code
docs/                         TEAM_BRIEF.md, GETTING_STARTED.md
pom.xml                       Java 21 + JUnit 5/Mockito + Jackson + SLF4J + Javalin + shade
sandbox/Dockerfile            least-privilege image for executing untrusted project code
src/main/java/com/aireviewer/ project, configuration, persistence, analysis, llm,
                              security, report, application, web
src/test/java/com/aireviewer/ mirrored test packages
frontend/                     React + Vite app (role 6) — not under src/main/java
```
