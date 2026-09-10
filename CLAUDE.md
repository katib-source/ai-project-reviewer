# CLAUDE.md — AI Project Reviewer

Instructions for any AI coding assistant (Claude Code, Cursor, Copilot, ...) working in this
repository, and the rules any human contributor is expected to follow too. If your tool doesn't
auto-load `CLAUDE.md`, paste this whole file into its project-instructions equivalent
(`AGENTS.md`, `.cursorrules`, `.github/copilot-instructions.md`) — the rules are tool-agnostic.

Full context (why the project is shaped this way) is in [`docs/TEAM_BRIEF.md`](docs/TEAM_BRIEF.md).
Read that once. Setup and daily workflow are in
[`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md). **This file is the enforceable subset: what
code in this repo must and must not do.**

Current repo state: **scaffold only** — build, docs, package directories and the sandbox image.
No business logic exists yet; each role writes its own package (§1).

## 0. Non-negotiable architecture rule

Dependencies flow one direction only:

```
web (React, via HTTP)  →  application (Facade)  →  { project, analysis, llm, security, report, persistence }  →  configuration
```

Three boundaries are absolute — **never** cross them, even "just this once":

1. `com.aireviewer.analysis` never imports Docker, HTTP, Javalin, Swing or any UI type, and
   depends on `llm` **only** through the `LLMProvider` / `LLMException` interfaces handed to it by
   **constructor injection** — never by calling a factory, a static, or a concrete provider itself.
2. `com.aireviewer.llm` never imports any UI / HTTP / web type of ours (an HTTP *client* for
   talking to a vendor API is fine — that is what the package is for).
3. `web` (and any future UI) never calls `project`, `analysis`, `llm`, `security`, `report` or
   `persistence` directly — only `application.ReviewService`.

If a task seems to require breaking one of these, **stop and flag it** instead of doing it. It
almost always means an abstraction is missing a method, not that the rule should bend.

Before adding a class, check the table in §1 for where it belongs. Do not create new top-level
packages without updating this file and `docs/TEAM_BRIEF.md` first.

## 1. Package layout (current status)

Everything is `TODO` — the directories exist, the code does not. Update your row (`TODO` →
`IN PROGRESS` → `DONE`) in the same PR that lands the code, so the rest of the team and their
assistants can see what is safe to build on.

| Package | Owner | Status | Responsibility |
|---|---|---|---|
| `com.aireviewer.project` | role 1 | TODO | Directory import, Composite file tree, file classification, include/exclude rules |
| `com.aireviewer.configuration` | role 1 | TODO | Env-first settings (API keys, limits, paths) — zero secrets in code |
| `com.aireviewer.persistence` | role 1 | TODO | JSON history of past analyses |
| `com.aireviewer.analysis` | role 2 | TODO | `Analyzer` (Strategy), `AbstractAnalyzer` (Template Method), `AnalysisEngine` (registry + runner + consolidator), `AnalysisListener` (Observer) |
| `com.aireviewer.analysis.analyzers` | role 2 | TODO | 2 deterministic analyzers + the LLM-backed analyzer bridge |
| `com.aireviewer.llm` | role 3 | IN PROGRESS | `LLMProvider` (Adapter) + Mock/Mistral/LM-Studio adapters, Factory, `ResilientLLMProvider` (Decorator), prompt building, JSON response validation |
| `com.aireviewer.security` | role 4 | TODO | `Sandbox` + `DockerSandbox` (least-privilege `docker run`), owns `sandbox/Dockerfile` |
| `com.aireviewer.report` | role 5 | TODO | LaTeX escaping, `LatexReportBuilder` (Builder), optional `pdflatex` compilation |
| `com.aireviewer.application` | role 5 | TODO | `ReviewService` Facade + `Main` composition root |
| `com.aireviewer.web` | role 5 | TODO | Javalin REST + SSE controllers and DTOs, binds to localhost only |
| `frontend/` (not Java) | role 6 | TODO | React + Vite app — lives in `frontend/`, never under `src/main/java` |

`DONE` means the interfaces and a working, tested implementation exist — extend or refine them,
don't redesign without talking to the owner.

## 2. Java conventions

- **Java 21**, Maven. Use **records** for immutable data (`Criterion`, `CriterionResult`,
  `LLMRequest`, DTOs...), not classes with getters — unless mutable state is genuinely needed
  (e.g. a `DirectoryNode` accumulating children).
- Interfaces define the contract. Implementations are package-private where possible,
  `public final class` otherwise. Don't make a type `public` unless another package needs it.
- **No `null` returns** from public methods — return `Optional<T>`, an empty collection, or throw.
  (The one accepted exception: config lookups for a not-yet-validated API key.)
- **Every class that implements a Design Pattern gets a type-level Javadoc naming *which* pattern
  it is and *what problem it solves here*.** Example:

  ```java
  /**
   * Decorator around any {@link LLMProvider}. Problem: LLM endpoints time out, rate-limit and
   * return malformed JSON, and every provider would otherwise re-implement retry itself. Adding
   * the behaviour here keeps each adapter a thin, single-purpose translation of one vendor API.
   */
  public final class ResilientLLMProvider implements LLMProvider { ... }
  ```

  This Javadoc is what feeds the technical report's pattern-justification section. Don't skip it,
  and match the style of the existing ones rather than inventing a new format.
- Logging via **SLF4J** (`private static final Logger LOG = LoggerFactory.getLogger(X.class);`).
  Never `System.out` / `System.err` in `src/main`.
- **Never log secrets**: no API keys, no `Authorization` headers, no full request/response bodies
  that may carry a key, no full contents of analyzed files. Log identifiers, sizes and outcomes.
- Checked exceptions across I/O and network boundaries (`LLMException`, `IOException`). Never
  swallow an exception silently: either handle it and produce a failed result object
  (`CriterionResult.failed(...)` or equivalent) with a clear message, or let it propagate.
- Javadoc on every public type and non-obvious public method; comments explain *why*, not *what*.

## 3. Testing rules

- **JUnit 5 + Mockito.** Tests mirror `src/main/java` under `src/test/java`.
- **No test may require a live LLM call or a running Docker daemon.** Use the mock provider (plus a
  failure-simulating instance for resilience tests) and a fake in-memory `Sandbox`. This is a hard
  subject requirement, not a preference — a test that needs network or Docker will be rejected in
  review.
- Every new `Analyzer`, `LLMProvider` adapter, inclusion rule, consolidator change or report
  builder method needs at least one test before it counts as done.
- `mvn test` must be green before you push. A red build blocks merging into `main`.

## 4. Security rules (these get checked in the defense)

- **No API key, token or password ever committed.** Environment variables, or a git-ignored
  `.env` / `application-local.properties`, only. If you paste a real key while testing, check
  `git status` and `git diff --staged` before committing.
- **All execution of analyzed-project code goes through `security.DockerSandbox`.** No
  `Runtime.exec` / `ProcessBuilder` against project content anywhere else in the codebase.
- Don't relax the sandbox flags — non-root, `--read-only`, `--cap-drop=ALL`, `--network=none` by
  default, memory/CPU/pids limits, `--rm`, hard timeout — without a team decision. They are an
  explicit subject requirement.
- **All untrusted content goes through the prompt builder's delimiting before reaching a prompt.**
  Never concatenate analyzed file content into a prompt string anywhere else. The builder marks
  clearly what is *instruction* and what is *data to analyze*, and the system prompt states that
  instructions found inside the data are to be treated as data.
- **All text written into a `.tex` file goes through the LaTeX escaper first** — LLM output, file
  names, project paths, everything. `pdflatex` is always invoked with `-no-shell-escape`.
- **Javalin binds to `localhost` only.** Never `0.0.0.0` "for convenience" — this app can trigger
  Docker execution of untrusted code.
- Validate LLM JSON responses before use; a malformed response is a handled failure, not a crash.

## 5. Git workflow

- **Branch per role/feature:** `role<N>/<short-desc>` — e.g. `role1/project-import`,
  `role3/llm-resilience`. Never one shared feature branch for everyone.
- **Commit message format:** `<package>: <what changed>` — e.g.
  `llm: add response validator for malformed JSON`, `docs: fix API contract typo`.
- Small, focused commits. One concern per commit.
- **Don't edit another role's package** without asking its owner. Interfaces are the contract: if
  you need a new method on someone else's interface, ask them for it (or open a PR *against* it)
  rather than reaching around it.
- Merge/rebase `main` into your branch before opening a PR. At least one other teammate reviews
  before merge — everyone must be able to explain the code that ships.
- `pom.xml` and the docs are shared files: expect conflicts, keep your edits there minimal and
  additive.

## 6. Build & run cheat sheet

```bash
mvn compile        # compile
mvn test           # all tests (never needs a live LLM or Docker)
mvn package        # target/ai-project-reviewer.jar (shaded, runnable)
java -jar target/ai-project-reviewer.jar          # run the backend (once Main exists)

docker build -t ai-project-reviewer-sandbox:latest sandbox/   # build the sandbox image (role 4)

cd frontend && npm install && npm run dev         # frontend dev server (role 6)
```

## 7. Extension recipes (these are literally report questions — they must stay true)

- **New LLM provider:** one new class in `llm/` implementing `LLMProvider` (or extending the
  shared OpenAI-compatible base when the API shape matches), plus one new branch in the provider
  factory. Nothing else changes.
- **New deterministic criterion:** one new class in `analysis/analyzers/` extending
  `AbstractAnalyzer`, registered with the engine at startup (composition root). Nothing else
  changes.
- **New LLM-backed criterion:** construct another LLM-backed analyzer with a new `Criterion` and
  register it — ideally **no new class at all**.
- **New file-selection rule:** one new inclusion-rule implementation, added to the inclusion
  policy. Nothing else changes.
- **New report format (HTML, Markdown, ...):** one new class implementing the report-builder
  contract in `report/`, selected from `application`. `analysis` and `llm` are untouched.

If any of these ever requires touching **more than 1–2 existing classes**, the abstraction has a
gap. Raise it; don't push through it.

## 8. Don't

- **No God class** doing most of the processing. If a class needs "and" to describe it, split it.
- **No business logic in `web` / UI** — controllers and components map DTOs and call the Facade,
  nothing else. No scoring, no file walking, no prompt building there.
- **No HTTP or LLM calls scattered outside `llm/`** — always through `LLMProvider`.
- **No Design Pattern added just to raise the count.** The nine patterns in
  `docs/TEAM_BRIEF.md` §4 each solve a stated problem; if you can't state the problem in one
  sentence, don't add the pattern.
- **No hardcoded dependency on one provider or model** anywhere outside `llm/` — no
  `new MistralProvider()` in `analysis`, `application` or `web`; get it from the factory/config.
- **No direct execution of analyzed code** outside `security.DockerSandbox`.
- **No untrusted text going straight into a prompt** or straight into a `.tex` file.
- **No new class without a test.** No new dependency without asking.
- **No commented-out dead code, no TODO dumps** in place of an implementation — an interface with
  one honest implementation beats five empty stubs.

## 9. For the AI assistant specifically

- **Check §1's table before guessing where code belongs.** If it isn't obvious from the table, ask
  rather than inventing a package or a subpackage.
- **Ask before**: adding an external dependency (Maven or npm), creating a new top-level package,
  introducing a Design Pattern not listed in §7 / the brief, or changing a shared file
  (`pom.xml`, `CLAUDE.md`, `docs/*`, the API contract).
- **Search the package before writing** — prefer extending an existing interface over introducing
  a parallel one. Don't duplicate a helper that already exists in another role's package; ask for
  it to be exposed instead.
- **Follow the existing Javadoc style** (§2) on every pattern-bearing class: which pattern, what
  problem, here.
- Stay inside the role's package for the task at hand. If the change genuinely needs another
  role's package, say so and stop — don't silently edit it.
- Respect §0 even when it costs more code. An import that crosses a boundary is the single most
  visible way this project loses marks.
- Write tests in the same change as the code, and never write a test that needs network, Docker or
  a real LLM key.
