# CLAUDE.md — AI Project Reviewer

Instructions for any AI coding assistant (Claude Code, Cursor, Copilot, etc.)
working in this repository. If your tool doesn't auto-load `CLAUDE.md`,
paste this whole file into its project-instructions equivalent
(`.cursorrules`, `AGENTS.md`, `.github/copilot-instructions.md`...) — the
rules are tool-agnostic.

Full context (why the project is shaped this way) is in
`docs/TEAM_BRIEF.md`. Read that once. This file is the enforceable subset:
what any AI-generated code in this repo must and must not do.

## 0. Non-negotiable architecture rule

Dependencies flow one direction only:

```
web (React, via HTTP)  →  application (Facade)  →  { project, analysis, llm, security, report, persistence }  →  configuration
```

Two boundaries are absolute — **never** cross them, even "just this once":

- Nothing in `com.aireviewer.analysis` may import anything from `com.aireviewer.llm`
  except the `LLMProvider`/`LLMException` interfaces it is given by
  constructor injection, and nothing in `analysis` may import Docker,
  HTTP, Swing, or Javalin types.
- Nothing in `com.aireviewer.llm` may import a UI/HTTP/web type.
- `ui`/`web` never calls `project`, `analysis`, `llm`, `security`, `report`,
  or `persistence` directly — only `application.ReviewService`.

If a task seems to require breaking one of these, stop and flag it instead
of doing it — it almost always means the abstraction is missing a method,
not that the rule should bend.

**Before adding a class, check this table for where it belongs.** Do not
create new top-level packages without updating this file and
`docs/TEAM_BRIEF.md` first.

## 1. Package layout (current status)

```
com.aireviewer
├── project/        [DONE — role 1]   Composite file tree, import, classification, inclusion rules
├── configuration/  [DONE — role 1]   AppConfig, ConfigSource (env-first, no secrets)
├── persistence/    [TODO — role 1]   JSON history of past analyses
├── analysis/        [DONE — role 2]   Analyzer/AbstractAnalyzer/AnalysisEngine/AnalysisListener + 2 deterministic analyzers
│   └── analyzers/   [DONE — role 2]   + LlmCriterionAnalyzer (bridges to llm/)
├── llm/             [DONE — role 3]   LLMProvider + adapters + Factory + Decorator + validator + PromptBuilder
├── security/         [DONE — role 4]   Sandbox + DockerSandbox + sandbox/Dockerfile
├── report/           [IN PROGRESS — role 5]  LatexEscaper, ReportMetadata done; LatexReportBuilder + LatexCompiler TODO
├── application/       [TODO — role 5]   ReviewService Facade, Main composition root
├── web/                [TODO — role 5]   Javalin REST + SSE controllers, DTOs
└── (separate) frontend/  [TODO — role 6]  Vite + React app, not under src/main/java
```

"DONE" means the interfaces and a working implementation exist — extend or
refine, don't redesign without discussion. "TODO" is open; follow the
conventions below when building it.

## 2. Java conventions

- Java 21. Use records for immutable data (`Criterion`, `CriterionResult`,
  `LLMRequest`, ...), not classes with getters, unless mutable state is
  genuinely needed (e.g. `DirectoryNode`, which accumulates children).
- Interfaces define the contract; implementations are package-visible where
  possible, `public final class` otherwise. Don't make a class `public`
  unless another package needs it.
- No `null` returns from public methods — use `Optional<T>`, an empty
  `List`, or throw. `AppConfig`/`ConfigSource` are the one accepted
  exception (nullable API keys before validation).
- Every class that implements a Design Pattern gets a short Javadoc on the
  type explaining *which* pattern and *what problem it solves here* — copy
  the style already used in `project`, `analysis`, `llm`, `security`. This
  Javadoc is what feeds the technical report's pattern-justification
  section; don't skip it.
- Logging via SLF4J (`LoggerFactory.getLogger(...)`), never `System.out`
  in `src/main`. **Never log API keys, request/response bodies containing
  secrets, or full file contents.**
- Checked exceptions for anything that crosses an I/O or network boundary
  (`LLMException`, `IOException`); don't swallow an exception silently —
  either handle it and produce a `CriterionResult.failed(...)`/equivalent,
  or let it propagate with a clear message.

## 3. Testing rules

- JUnit 5 + Mockito. Tests mirror `src/main/java` structure under
  `src/test/java`.
- **No test may require a live LLM call or a running Docker daemon.** Use
  `MockLLMProvider` (and a `simulateFailure=true` instance for resilience
  tests) and a fake `Sandbox` implementation. This is a hard subject
  requirement (section 11), not a suggestion.
- Every new `Analyzer`, `LLMProvider` adapter, inclusion `Rule`, or report
  builder method needs at least one test before it's considered done.
- Run `mvn test` before pushing. A red build blocks merging into `main`.

## 4. Security rules (non-negotiable, these get checked in the defense)

- No API key, password, or token ever committed — env vars or
  `application-local.properties` (git-ignored) only. If you paste a real
  key while testing locally, double-check `git status` before committing.
- Any execution of code belonging to an analyzed project goes through
  `security.DockerSandbox` — never `Runtime.exec`/`ProcessBuilder` directly
  against project content elsewhere in the codebase.
- Don't relax the Docker flags in `DockerSandbox` (non-root, read-only,
  `--cap-drop=ALL`, `--network=none`, memory/CPU/pids limits) without a
  team discussion — they're there because the subject requires them
  explicitly (section 8.1).
- Any text pulled from an analyzed project and sent to an LLM must go
  through `PromptBuilder`'s delimiting, never be concatenated straight into
  a prompt string elsewhere.
- Any text (LLM output, file names, project content) written into a `.tex`
  file must go through `LatexEscaper` first. `pdflatex` is invoked with
  `-no-shell-escape`, always.
- The Javalin server binds to `localhost` only. Do not change this to
  `0.0.0.0` for "convenience."

## 5. Git workflow

- Branch per role/feature: `role1/project-import`, `role3/llm-resilience`,
  etc. — not one shared branch for everyone.
- Small, focused commits. Message format: `<package>: <what changed>`
  (e.g. `llm: add response validator for malformed JSON`).
- Don't edit another package's internals without pinging its owner —
  interfaces are the contract; if you need a new method on someone else's
  interface, ask them to add it (or open a PR against it) rather than
  reaching around it.
- Rebase/merge `main` into your branch before opening a PR; at least one
  other teammate reviews before merge given the "everyone must be able to
  explain the code" rule.

## 6. Build & run cheat sheet

```
mvn compile                 # compile
mvn test                    # run all tests (no LLM/Docker needed)
mvn package                 # build target/ai-project-reviewer.jar (shaded, runnable)
docker build -t ai-project-reviewer-sandbox:latest sandbox/   # build the sandbox image (role 4)
java -jar target/ai-project-reviewer.jar                      # run the backend (once Main exists)
```

Frontend (once scaffolded, role 6): `npm install && npm run dev` for local
dev against `mvn`-run backend; `npm run build` output gets copied into the
Java resources so the shaded jar can serve it standalone.

## 7. Extension recipes (must stay true — they're literally report questions)

- **New LLM provider:** one new class in `llm/` extending
  `OpenAiCompatibleLLMProvider` (or implementing `LLMProvider` directly if
  the API shape differs), plus one new branch in `LLMProviderFactory`.
  Nothing else changes.
- **New evaluation criterion (deterministic):** one new class in
  `analysis/analyzers/` extending `AbstractAnalyzer`, registered with
  `AnalysisEngine.register(...)` at startup. Nothing else changes.
- **New evaluation criterion (LLM-backed):** construct another
  `LlmCriterionAnalyzer` with a new `Criterion`, register it. No new class
  needed at all.
- **New file-selection strategy:** one new `InclusionRule` implementation,
  added to a `FileInclusionPolicy`. Nothing else changes.
- **New report format:** one new class implementing the report-building
  contract in `report/`, called from `application`. `analysis` and `llm`
  are untouched.

If completing any of the above ever requires touching more than 1–2
existing classes, that's a signal the abstraction has a gap — raise it,
don't just push through it.

## 8. Don't (mirrors the subject's own "be careful" list — section 16.2)

- No God class doing most of the processing.
- No business logic inside `web`/`ui` controllers/components — they map
  DTOs and call the Facade, nothing else.
- No HTTP/LLM calls scattered outside `llm/` — always through
  `LLMProvider`.
- No Design Pattern added just to raise the count — every pattern in
  `docs/TEAM_BRIEF.md` §4 solves a stated problem; if you can't state the
  problem, don't add it.
- No hard dependency on one specific LLM model/provider anywhere outside
  `llm/`.
- No direct execution of an analyzed project's code outside
  `security.DockerSandbox`.
- No missing tests for a new class — see §3.

## 9. For the AI assistant specifically

- Prefer editing/extending existing interfaces over introducing parallel
  ones. Search the package first.
- Ask before adding a new external dependency (Maven or npm) — keep the
  footprint minimal given the 24h budget.
- Ask before introducing a new top-level package or a new Design Pattern
  not already listed above.
- When generating a new class, include the pattern-justification Javadoc
  style shown in the existing `project`/`analysis`/`llm`/`security` code.
- When unsure which package something belongs in, check §1's table before
  guessing.
