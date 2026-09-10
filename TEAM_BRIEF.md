# AI Project Reviewer — Architecture & Team Plan

M1 Software Architecture & AI project. 24h, team of 6. This doc is the shared
reference for the whole team — read your section, but skim the rest so you
know what everyone else is building against.

## 1. What we're building

A Java application that automatically evaluates a software project (source
code) using both deterministic checks and real LLM calls, then produces a
LaTeX evaluation report. Pipeline: **import a project → explore its files →
pick evaluation criteria → run analyses → consolidate scores → generate a
`.tex` (and ideally `.pdf`) report → keep a history.**

The grading is explicitly **not** about how clever the analysis is. The
subject says outright: *"the quality of the project will be assessed
primarily on your ability to master this architecture and justify the
decisions that led to its design."* Every choice below is driven by that.

## 2. The four constraints that shape everything

1. **LLM swappability** — today Mistral or a local model, tomorrow something
   else. Nothing outside the `llm` package may know which provider is in use.
2. **LLM unreliability** — timeouts, bad JSON, outages are the normal case,
   not the exception. Every LLM call needs retry + fallback + validation, and
   the whole app must be testable **without ever calling a real LLM**.
3. **Untrusted code, twice over** — (a) if we ever execute code from an
   analyzed project, it happens in a locked-down, disposable Docker
   container, never on our machines; (b) that same code is also untrusted
   *text* fed to an LLM prompt, so prompts must clearly separate "instructions
   to the model" from "data to analyze" (prompt injection defense).
4. **Extensibility without rewrites** — adding a new criterion, LLM provider,
   or report type should mean *adding* a class, never editing five existing
   ones.

## 3. Architecture overview

Layered architecture, one package per responsibility, dependencies flow one
direction only:

```
ui/web  →  application (Facade)  →  { project, analysis, llm, security, report, persistence }  →  configuration
```

`ui` (the React app, talking over HTTP) never touches `project`, `analysis`,
`llm`, or `security` directly — only the `ReviewService` Facade in
`application`. Two boundaries are held to a stricter, "hexagonal" rule
because they're what the grader will look at hardest:

- `analysis` must never import an HTTP or Docker type.
- `llm` must never import a web/UI type.

That's what makes "swap the LLM provider" or "swap the whole UI stack"
possible without touching business logic — and we're actually proving it,
since we're using a **web UI (React) instead of a native Swing/JavaFX one**,
which only works cleanly if this boundary is real.

### Packages

| Package | Responsibility |
|---|---|
| `project` | Import a directory, build a file tree (Composite), classify files, apply include/exclude rules |
| `analysis` | `Analyzer` strategies, the engine that runs a selection and consolidates scores, progress notifications |
| `llm` | Provider abstraction, concrete adapters, retry/fallback, prompt building (incl. injection defense), response validation |
| `security` | Docker sandbox for any execution of untrusted project code, least-privilege container config |
| `report` | Builds the LaTeX document from evaluation results, optional PDF compilation |
| `persistence` | JSON-based history of past analyses |
| `configuration` | Env-first settings (API keys, limits) — **never hard-coded secrets** |
| `application` | The `ReviewService` Facade tying every package together — the *only* thing the UI depends on |
| `web` | Javalin REST + SSE layer exposing the Facade to the React frontend |

## 4. Design patterns (and the actual problem each one solves)

| Pattern | Where | Problem it solves |
|---|---|---|
| Composite | `project` (`DirectoryNode`/`FileEntry`) | Uniformly walk/render a file tree of unknown depth |
| Strategy | `analysis` (`Analyzer`), `project` (inclusion rules) | Swap evaluation algorithms / file-selection rules without touching callers |
| Template Method | `analysis` (`AbstractAnalyzer`) | Every analyzer gets identical error handling for free — one throwing analyzer never crashes a run |
| Adapter | `llm` (`MistralLLMProvider`, `LocalLmStudioProvider`, `MockLLMProvider`) | One stable interface regardless of vendor API shape |
| Factory | `llm` (`LLMProviderFactory`) | Builds the configured provider from settings — no `new XyzProvider()` scattered around |
| Decorator | `llm` (`ResilientLLMProvider`) | Adds retry/timeout/fallback to *any* provider without subclassing each one |
| Observer | `analysis` (`AnalysisListener`) | Progress/errors reach the UI (Swing *or* an SSE stream) without the engine knowing what's listening |
| Builder | `report` (`LatexReportBuilder`) | Incrementally assembles a complex LaTeX document |
| Facade | `application` (`ReviewService`) | One simple entry point for the UI into the whole engine |

Nine is plenty — don't add a tenth just to pad the list. The subject
explicitly penalizes patterns that don't solve a real problem you have.

## 5. Tech stack

- **Java 21**, Maven, JUnit 5 + Mockito, Jackson (JSON), SLF4J (logging)
- **Javalin** for the REST + SSE layer (not Spring Boot — a fraction of the
  setup time, which matters on a 24h clock)
- **React + Vite** for the frontend (not Angular/CRA — fastest to scaffold,
  least ceremony)
- **Docker** for sandboxing untrusted project execution
- **LaTeX** (`pdflatex`) for report compilation, invoked with
  `-no-shell-escape`
- **LLM**: default to a `MockLLMProvider` for all day-to-day dev/tests; wire
  in a real Mistral key (or local LM Studio) near the end for the actual
  required "real LLM call"

## 6. API contract (backend ↔ frontend)

This is the shared interface — the React person can build against this
immediately with fake data, no need to wait on the backend.

- `POST /api/projects` `{path}` → `{projectId, tree}`
- `GET /api/criteria` → list of available criteria
- `POST /api/analyses` `{projectId, criterionIds}` → `{analysisId}`
- `GET /api/analyses/{id}/events` → **SSE** stream: `criterion-started`,
  `criterion-completed`, `analysis-completed`, `error`
- `GET /api/analyses/{id}` → full `EvaluationResult` JSON
- `POST /api/analyses/{id}/report` → generates the `.tex`/`.pdf`, returns a
  download URL
- `GET /api/analyses` → history list

Server binds to **localhost only** (never `0.0.0.0`) — this app can trigger
Docker execution of untrusted code, so it must not be reachable from the
network by default.

## 7. Team roles (6 people)

Work against the interfaces from day one — nobody needs to wait on anybody
else. Each role includes writing that module's own unit tests (the subject
requires every component be independently testable, without calling a real
LLM or Docker).

**1 — Project & Data.** Packages: `project`, `configuration`, `persistence`.
Directory import, the Composite file tree, file classification, include/exclude
rules, env-first config (no secrets committed), JSON history storage.
Smallest scope of the six — free to help role 5 once done.

**2 — Analysis Engine.** Package: `analysis`. The `Analyzer` interface,
`AbstractAnalyzer` (Template Method), `AnalysisEngine` (registry + runner +
consolidation), `AnalysisListener` (Observer contract), plus two working
deterministic analyzers (no LLM needed). This is the spine every criterion
plugs into.

**3 — LLM Integration.** Package: `llm`. Provider interface (Adapter),
Mock/Mistral/local adapters, the Factory, the resilience Decorator
(retry/timeout/fallback), prompt building with injection-defense delimiting,
JSON response validation. Heaviest single-package role — needs someone
comfortable with HTTP/JSON and careful error handling.

**4 — Security Sandbox.** Package: `security`. Docker isolation wrapper,
least-privilege `docker run` flags (non-root, read-only, no network, resource
limits, auto-remove), the sandbox `Dockerfile`. Self-contained; also owns the
security/threat-model section of the written report.

**5 — Backend API + Report + Facade.** Packages: `application`, `report`,
`web`. Builds the `ReviewService` Facade wiring every other package
together, the LaTeX `Builder` + PDF compilation, and exposes it all over
Javalin REST + SSE. This is the integration point everyone else's work lands
in — heaviest role, first to pull in help from role 1.

**6 — React Frontend.** Separate Vite project. Project picker, file tree
view, criteria checklist, live progress bar off the SSE stream, results
view, report download. Fully decoupled — start immediately against the API
contract above with mocked responses, swap in the real backend later.

## 8. Suggested timeline (24h)

- **Hours 0–1:** everyone reads this doc + the subject PDF once; confirm the
  API contract and package interfaces; agree on a shared repo + branch
  convention.
- **Hours 1–8:** build in parallel against interfaces/mocks. Target: each
  role has *something* runnable and unit-tested, even if minimal.
- **Hours 8–14:** first real integration pass — wire real modules behind the
  Facade, real SSE events flowing to a real (if rough) React screen, one
  real end-to-end run using the `MockLLMProvider`.
- **Hours 14–20:** swap in the real LLM provider, get Docker sandbox
  actually tested against a sample project, get `.tex` generation producing
  a real `evaluation.pdf`, round out tests.
- **Hours 20–24:** freeze features. Write/finish the technical report
  (architecture diagram, pattern justifications, the 9 required Q&A, task
  distribution), record/rehearse who explains what for the defense, final
  `mvn test` + full dry run from a clean clone.

## 9. Ground rules

- **No secrets in git, ever** — API keys via environment variables or a
  git-ignored `application-local.properties` only.
- Every module ships with tests that don't require Docker or a live LLM
  call (use the `MockLLMProvider` / a fake `Sandbox`).
- Keep the dependency rule honest: if you're tempted to import something
  from `analysis` into `llm` (or vice versa) or a web type into `analysis`,
  stop and ask — that's the one thing that will visibly undermine the whole
  architecture story in the defense.
- Everyone must be able to explain their own module's code and its design
  pattern(s) cold — the subject explicitly says code nobody can explain
  counts as "not mastered."
