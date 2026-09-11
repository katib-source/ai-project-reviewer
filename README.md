# AI Project Reviewer

A Java application that automatically evaluates a software project (its source code) using
**deterministic static analysis**, a **sandboxed build check**, and **real LLM calls**, then
generates a **LaTeX evaluation report**.

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

## Requirements

- **Java 21** and **Maven** — to build and run the backend.
- **Node 20+** — to build and run the frontend (`front/`).
- **Docker** *(optional)* — only needed for the sandboxed build-check criterion. Everything else
  runs without it; if the sandbox image isn't built or Docker isn't running, that one criterion
  reports an honest "couldn't run" result and the rest of the analysis proceeds normally.
- **A LaTeX distribution with `pdflatex`** *(optional)* — report generation always produces a
  `.tex` file; PDF compilation is a best-effort extra step that needs `pdflatex` on `PATH`.

## Build & run the backend

```bash
mvn clean package
java -jar target/ai-project-reviewer.jar
```

The server binds to `127.0.0.1:7070` — localhost only, by design (CLAUDE.md §4): this app can
trigger Docker execution of untrusted code, so it must never be reachable from the network by
default.

## Configuring the LLM provider

Settings are env-first (see `AppConfig`). The default needs no setup at all:

```bash
LLM_PROVIDER=mock       # default — deterministic, offline, no key required
```

To use a real model instead:

```bash
LLM_PROVIDER=mistral
MISTRAL_API_KEY=...

LLM_PROVIDER=groq       # has a free tier that needs no billing setup
GROQ_API_KEY=...

LLM_PROVIDER=local      # LM Studio or another OpenAI-compatible local server;
                        # defaults to http://localhost:1234/v1, no key needed
```

Whatever provider is selected is always wrapped in retry/timeout/fallback (`ResilientLLMProvider`),
falling back to the mock if the real call ultimately fails — one criterion degrades, the rest of
the analysis still completes.

A few other environment variables control file inclusion, size limits and where history is stored
(`PROJECT_ALLOWED_EXTENSIONS`, `PROJECT_MAX_FILE_SIZE_BYTES`, `PROJECT_IGNORED_DIRECTORY_NAMES`,
`PERSISTENCE_HISTORY_FILE_PATH`) — see `AppConfig` for the full list and defaults.

## Build the sandbox image (optional)

Only required for the build-check analyzer, which validates a project's build manifest
(`pom.xml`/`package.json`) inside a locked-down, network-isolated container rather than on the
host:

```bash
docker build -t ai-project-reviewer-sandbox:latest sandbox/
```

## Build & run the frontend

```bash
cd front
npm install
npm run dev
```

Starts the Vite dev server on `http://localhost:5173`, which proxies `/api` and `/reports` to the
backend on `127.0.0.1:7070` — no CORS configuration needed as long as both are running.

## Basic usage

1. **Import a project** — a local path, or a folder/zip upload.
2. **Select criteria** from the available list (deterministic checks, the sandboxed build check,
   and LLM-backed criteria).
3. **Run the analysis** — starts asynchronously; watch per-criterion progress live over SSE.
4. **View results** — per-criterion scores and comments, plus one consolidated weighted score.
5. **Generate a report** — produces a `.tex` file (always) and a `.pdf` (if `pdflatex` is
   available).
6. **Browse history** — past analyses are listed with their consolidated scores.

## More

- Architecture, design patterns and the reasoning behind them: [`docs/TEAM_BRIEF.md`](docs/TEAM_BRIEF.md)
- Enforceable rules for code (and AI assistants) in this repo: [`CLAUDE.md`](CLAUDE.md)
