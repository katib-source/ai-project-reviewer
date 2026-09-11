# AI Project Reviewer — Architecture & Team Plan

M1 Software Architecture & AI project. 24h, team of 6. This is the shared reference for the whole
team: read your section, skim the rest so you know what everyone else is building against.

Companion documents:
- [`../CLAUDE.md`](../CLAUDE.md) — the enforceable rules (auto-loaded by Claude Code). This brief
  explains *why*; that file is *what you must do*.
- [`GETTING_STARTED.md`](GETTING_STARTED.md) — machine setup and the daily loop.

## 1. What we're building

A Java application that automatically evaluates a software project (source code) using both
deterministic checks and real LLM calls, then produces a LaTeX evaluation report. Pipeline:
**import a project → explore its files → pick evaluation criteria → run analyses → consolidate
scores → generate a `.tex` (and ideally `.pdf`) report → keep a history.**

The grading is explicitly **not** about how clever the analysis is. The subject says outright:
*"the quality of the project will be assessed primarily on your ability to master this
architecture and justify the decisions that led to its design."* Every choice below follows from
that: low coupling, patterns that each solve a stated problem, provider interchangeability,
resilience, safe handling of untrusted code, and extensibility.

## 2. The four constraints that shape everything

1. **LLM swappability** — today Mistral or a local model, tomorrow something else. Nothing outside
   the `llm` package may know which provider is in use. Proof: swapping providers is a config
   change, and every other package compiles untouched.
2. **LLM unreliability** — timeouts, rate limits, outages and malformed JSON are the normal case,
   not the exception. Every call gets retry + timeout + fallback + response validation, and the
   whole app must be testable **without ever calling a real LLM**.
3. **Untrusted code, twice over** — (a) if we execute anything from an analyzed project it happens
   inside a locked-down, disposable Docker container, never on our machines; (b) that same code is
   also untrusted **text** fed into an LLM prompt, so prompts must clearly separate "instructions
   to the model" from "data to analyze" (prompt-injection defense). Both defenses are required —
   neither one substitutes for the other.
4. **Extensibility without rewrites** — adding a criterion, an LLM provider or a report type means
   *adding* a class, never editing five existing ones. See §10.

## 3. Architecture overview

Layered architecture, one package per responsibility, dependencies flow one direction only:

```
web (React, via HTTP)  →  application (Facade)  →  { project, analysis, llm, security, report, persistence }  →  configuration
```

The React app never touches `project`, `analysis`, `llm`, `security`, `report` or `persistence`
directly — only `ReviewService` in `application`. Two boundaries are held to a stricter,
"hexagonal" rule because they are what a grader looks at hardest:

- `analysis` never imports an HTTP, Docker or UI type, and reaches `llm` only through the
  `LLMProvider` interface it is constructor-injected with.
- `llm` never imports a web/UI type of ours.

That is what makes "swap the LLM provider" or "swap the whole UI stack" possible without touching
business logic — and we're actually proving the second one, since we use a **web UI (React)
instead of a native Swing/JavaFX one**, which only works cleanly if the boundary is real.

### Packages

| Package | Responsibility |
|---|---|
| `project` | Import a directory, build a file tree (Composite), classify files, apply include/exclude rules |
| `configuration` | Env-first settings (API keys, limits, timeouts) — **never hard-coded secrets** |
| `persistence` | JSON-based history of past analyses |
| `analysis` | `Analyzer` strategies, the engine that runs a selection and consolidates scores, progress notifications |
| `llm` | Provider abstraction, concrete adapters, retry/timeout/fallback, prompt building (incl. injection defense), response validation |
| `security` | Docker sandbox for any execution of untrusted project code, least-privilege container configuration |
| `report` | Builds the LaTeX document from evaluation results, optional PDF compilation |
| `application` | The `ReviewService` Facade tying every package together — the *only* thing the UI depends on; plus `Main`, the composition root |
| `web` | Javalin REST + SSE layer exposing the Facade to the React frontend, bound to localhost |

## 4. Design patterns (and the actual problem each one solves)

| Pattern | Where | Problem it solves |
|---|---|---|
| **Composite** | `project` (directory / file nodes) | Uniformly walk and render a file tree of unknown depth; a directory and a file answer the same questions |
| **Strategy** | `analysis` (`Analyzer`), `project` (inclusion rules) | Swap evaluation algorithms and file-selection rules without touching any caller |
| **Template Method** | `analysis` (`AbstractAnalyzer`) | Every analyzer inherits identical error handling, timing and result wrapping — one throwing analyzer can never crash a whole run |
| **Adapter** | `llm` (Mistral / LM Studio / Mock providers) | One stable interface regardless of each vendor's API and JSON shape |
| **Factory** | `llm` (provider factory) | Build the configured provider from settings, so no `new XyzProvider()` is scattered around the codebase |
| **Decorator** | `llm` (`ResilientLLMProvider`) | Add retry / timeout / fallback to *any* provider without subclassing each one, and keep each adapter a thin translation layer |
| **Observer** | `analysis` (`AnalysisListener`) | Progress and errors reach the UI (an SSE stream today, anything tomorrow) without the engine knowing what is listening |
| **Builder** | `report` (`LatexReportBuilder`) | Incrementally assemble a long, structured LaTeX document with many optional sections |
| **Facade** | `application` (`ReviewService`) | One simple entry point for the UI into the whole engine, so the UI has exactly one dependency |

Nine is plenty — **don't add a tenth to pad the list.** The subject explicitly penalizes patterns
that don't solve a problem you actually have.

## 5. Tech stack

- **Java 21**, Maven, JUnit 5 + Mockito, Jackson (JSON), SLF4J (logging), maven-shade-plugin
  (runnable jar, main class `com.aireviewer.Main`)
- **Javalin** for the REST + SSE layer (not Spring Boot — a fraction of the setup time, which
  matters on a 24h clock)
- **React + Vite** for the frontend, in `frontend/` (not Angular/CRA — fastest to scaffold)
- **Docker** for sandboxing untrusted project execution (`sandbox/Dockerfile`)
- **LaTeX** (`pdflatex`, invoked with `-no-shell-escape`) for report compilation
- **LLM**: a mock provider for all day-to-day dev and every test; a real Mistral key (or local LM
  Studio) wired in near the end for the required real LLM call

## 6. API contract (backend ↔ frontend)

The shared interface. Role 6 builds against this immediately with fake data — no waiting on the
backend.

| Method & path | Request | Response |
|---|---|---|
| `POST /api/projects` | `{path}` | `{projectId, tree}` |
| `POST /api/projects/upload` | multipart: one `files` part per file, filename = path relative to the chosen folder | `{projectId, tree}` |
| `GET /api/criteria` | — | list of available criteria |
| `POST /api/analyses` | `{projectId, criterionIds}` | `{analysisId}` |
| `GET /api/analyses/{id}/events` | — | **SSE**: `criterion-started`, `criterion-completed`, `analysis-completed`, `error` |
| `GET /api/analyses/{id}` | — | full evaluation result JSON |
| `POST /api/analyses/{id}/report` | — | generates `.tex`/`.pdf`, returns a download URL |
| `GET /api/analyses` | — | history list |

The server binds to **localhost only** (never `0.0.0.0`): this app can trigger Docker execution of
untrusted code, so it must not be reachable from the network by default.

## 7. Team roles (6 people)

Work against the interfaces from day one — nobody needs to wait on anybody else. Every role
includes that module's own unit tests: each component must be independently testable **without a
real LLM call and without Docker**.

**Role 1 — Project & Data.** Packages `project`, `configuration`, `persistence`.
- Import a directory path; reject a missing/unreadable path with a clear error.
- Composite file tree (directory node + file node, one shared interface), size/type classification.
- Inclusion rules as Strategy: extension filter, size cap, ignore `target/`, `node_modules/`, `.git/`.
- `AppConfig` read env-first (API keys, timeouts, limits) with sane defaults and **zero secrets in code**.
- JSON history store (Jackson): append a completed analysis, list history, load one by id.
- Tests: tree building over a temp directory, each rule, config precedence, round-trip persistence.

**Role 2 — Analysis Engine.** Package `analysis`.
- `Analyzer` interface (Strategy) + `Criterion` / `CriterionResult` records.
- `AbstractAnalyzer` (Template Method): timing, logging, exception → failed result, so no analyzer can crash a run.
- `AnalysisEngine`: register analyzers, run a selected subset, consolidate weighted scores into one evaluation result.
- `AnalysisListener` (Observer) contract + notification points: started / completed / error per criterion.
- Two working deterministic analyzers (no LLM), e.g. file/size statistics and comment-or-naming checks.
- The LLM-backed analyzer bridge: takes an `LLMProvider` **by constructor injection**, never imports a concrete provider.
- Tests: consolidation maths, a deliberately throwing analyzer, listener notification order, LLM analyzer against a mock provider.

**Role 3 — LLM Integration.** Package `llm`. Heaviest single package — needs someone comfortable
with HTTP/JSON and careful error handling.
- `LLMProvider` interface (Adapter) + `LLMException`; request/response records.
- `MockLLMProvider` (deterministic answers, plus a failure-simulating mode) — this is what every test uses.
- Mistral adapter and local LM Studio adapter over `java.net.http.HttpClient`; keys from `configuration`, never logged.
- Provider factory selecting from config; unknown provider name → clear failure at startup.
- `ResilientLLMProvider` (Decorator): timeout, bounded retry with backoff, fallback to another provider (typically mock), all logged.
- Prompt builder with explicit delimiting of untrusted content + a system instruction that data is never an instruction.
- JSON response validation: score range, required fields, malformed output → typed failure, not a crash.
- Tests: all of the above with zero network calls.

**Role 4 — Security Sandbox.** Package `security`. Self-contained; also owns the security /
threat-model section of the written report.
- `Sandbox` interface (+ a fake implementation for other roles' tests) and a `SandboxResult` record.
- `DockerSandbox`: least-privilege `docker run` — non-root, `--read-only`, `--cap-drop=ALL`,
  `--network=none` by default, memory/CPU/pids limits, `--rm`, project mounted read-only, hard
  wall-clock timeout that actually kills the container.
- `sandbox/Dockerfile` (already scaffolded): minimal JDK+Maven image, uid/gid 1000, no ENTRYPOINT.
- Tests: command-line construction asserted flag by flag (no Docker daemon needed), timeout path, non-zero exit handling.
- Write up the threat model: what we trust, what we don't, what an attacker could still do.

**Role 5 — Backend API + Report + Facade.** Packages `application`, `report`, `web`. The
integration point where everyone's work lands — heaviest role, first to pull in help from role 1.
- `ReviewService` (Facade): import project, list criteria, start analysis (async + listener), fetch result, generate report, list history.
- `Main`: the composition root — the only place that wires concrete implementations together.
- LaTeX escaper (every special character), `LatexReportBuilder` (Builder) producing a complete `.tex`, optional `pdflatex -no-shell-escape` compilation with a timeout.
- Javalin REST controllers matching §6 exactly + the SSE endpoint bridging `AnalysisListener` to the browser; **localhost bind**; thin DTO mapping, zero business logic.
- Tests: escaper edge cases, builder output structure, Facade against mocked collaborators.

**Role 6 — React Frontend.** `frontend/` (Vite project, never under `src/main/java`).
- Project picker (folder upload via `POST /api/projects/upload`, or path input via
  `POST /api/projects`) → file tree view.
- Criteria checklist from `GET /api/criteria`; start an analysis.
- Live progress from the SSE stream (per-criterion status, error events).
- Results view: per-criterion scores, comments, consolidated score; report download button.
- History list. Fully decoupled: start immediately against §6 with mocked responses, swap in the real backend later.

## 8. Suggested timeline (24h)

- **Hours 0–1 — setup.** Everyone reads this doc + `CLAUDE.md` + the subject once. Confirm the API
  contract and package interfaces. Clone, `mvn compile`, branch conventions agreed.
- **Hours 1–8 — parallel build.** Build against interfaces and mocks. Target: every role has
  *something* runnable and unit-tested, even if minimal. Interfaces land on `main` early so others
  can compile against them.
- **Hours 8–14 — first integration pass.** Real modules wired behind the Facade, real SSE events
  reaching a rough React screen, one full end-to-end run using the mock LLM provider.
- **Hours 14–20 — real wiring.** Swap in the real LLM provider (one real call, verified), Docker
  sandbox tested against a sample project, `.tex` generation producing a real `evaluation.pdf`,
  tests rounded out.
- **Hours 20–24 — freeze & write.** Feature freeze. Finish the technical report (architecture
  diagram, pattern justifications, the required Q&A, task distribution), rehearse who explains
  what, final `mvn test` + a full dry run from a clean clone.

## 9. Ground rules

- **No secrets in git, ever** — env vars or a git-ignored local properties file only.
- Every module ships tests that need **neither Docker nor a live LLM** (mock provider, fake sandbox).
- **Keep the dependency rule honest.** If you're tempted to import a Docker/HTTP type into
  `analysis`, or a web type into `llm`, or call `analysis` straight from `web` — stop and ask.
  That is the single most visible way this project loses marks.
- Don't edit another role's package without asking. Interfaces are the contract.
- Update your row in the `CLAUDE.md` package table when your status changes.
- **Everyone must be able to explain their own module's code and its patterns cold** — the subject
  says code nobody can explain counts as "not mastered."

## 10. Extensibility, as it will be asked in the defense

| "How would you add..." | Answer |
|---|---|
| a new LLM provider | one adapter class in `llm/` + one branch in the factory |
| a new deterministic criterion | one class in `analysis/analyzers/` + one registration line |
| a new LLM-backed criterion | one new `Criterion` value — no new class |
| a new file-selection rule | one inclusion-rule class added to the policy |
| a new report format | one class implementing the report contract in `report/` |
| a different UI (CLI, Swing) | a new caller of `ReviewService` — nothing else changes |

If any of those ever needs more than 1–2 existing classes touched, the abstraction has a gap.
