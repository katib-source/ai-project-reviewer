package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.AbstractAnalyzer;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.project.FileNode;
import com.aireviewer.security.SandboxResult;
import com.aireviewer.security.SandboxRunner;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic criterion that wires {@link SandboxRunner} into the analysis pipeline for the
 * first time. Constructor-injects the interface, never a concrete {@code DockerSandboxRunner} —
 * {@code Main} decides which implementation to use, the same shape as
 * {@link LLMBackedAnalyzer}'s relationship to {@code LLMProvider}.
 * <p>
 * <b>What this actually checks, versus what its name suggests:</b> {@code DockerSandboxRunner}
 * runs with {@code --network none} and mounts the project read-only (see its own source). That
 * rules out a real compile or install categorically, not just as a speed concern — no
 * network-dependent dependency resolution can succeed, and no step that writes build output
 * (Maven's {@code target/}, npm's {@code node_modules/}) can write into a read-only mount. So
 * this class can only ever check that the project's build manifest is structurally valid and
 * that the toolchain accepts it as a starting point — never that the project actually builds.
 * The name is kept because that is what this criterion was asked for, but this gap is stated here
 * explicitly rather than left for a reader to discover.
 * <p>
 * For a Maven project, the command run is {@code mvn -q -o -Dmaven.repo.local=/tmp/m2-repo
 * validate}: {@code validate} is the last lifecycle phase before anything starts writing to
 * {@code target/}, so it is the one phase compatible with the read-only mount regardless of the
 * project; {@code -Dmaven.repo.local} redirects Maven's own repository cache to the sandbox's one
 * writable location ({@code --tmpfs /tmp}), since without it Maven cannot even initialize itself
 * under a read-only home directory; {@code -o} makes an unresolvable dependency fail with a clear
 * message instead of a raw network error. {@code mvn -v} was deliberately not used: it reports the
 * sandbox image's Maven version, identical for every project regardless of content, so it checks
 * nothing about the analyzed project at all.
 * <p>
 * For an npm-style project, the command is {@code node -e
 * "JSON.parse(require('fs').readFileSync('package.json','utf8'))"} rather than any {@code npm}
 * command: it needs only {@code node} (the sandbox image is scoped as "minimal JDK+Maven" per
 * {@code docs/TEAM_BRIEF.md}, with no guarantee npm is even present), touches no network, and
 * writes nothing, so it is compatible with both constraints by construction. It only checks that
 * the manifest is valid JSON — shallow, but honest about what it verifies.
 */
public final class BuildCheckAnalyzer extends AbstractAnalyzer {

    private static final Criterion CRITERION = new Criterion(
            "build-check",
            "Build Check",
            "Checks whether the project's build manifest (pom.xml or package.json) is "
                    + "structurally valid and accepted by its build toolchain, run inside the "
                    + "sandbox with no network access and a read-only project mount — this does "
                    + "not attempt a real compile or install, which the sandbox cannot support.",
            1.0
    );

    /** Fast, local-only, no-network check; a generous ceiling rather than a tuned value. */
    private static final Duration SANDBOX_TIMEOUT = Duration.ofSeconds(30);

    /**
     * {@code DockerSandboxRunner}'s own sentinel for "could not run the sandbox at all"
     * (an {@code IOException} or {@code InterruptedException} before any subprocess exit code
     * exists) — unambiguous, since a real subprocess exit code is always in {@code [0, 255]}.
     */
    private static final int SANDBOX_INTERNAL_ERROR_EXIT_CODE = -1;

    /** Keeps a comment built from unbounded sandbox output bounded, mirroring llm's own snippet limit. */
    private static final int MAX_OUTPUT_CHARS = 500;

    private static final List<String> MAVEN_VALIDATE_COMMAND =
            List.of("mvn", "-q", "-o", "-Dmaven.repo.local=/tmp/m2-repo", "validate");
    private static final List<String> NODE_MANIFEST_CHECK_COMMAND =
            List.of("node", "-e", "JSON.parse(require('fs').readFileSync('package.json','utf8'))");

    private final SandboxRunner sandboxRunner;

    public BuildCheckAnalyzer(SandboxRunner sandboxRunner) {
        this.sandboxRunner = Objects.requireNonNull(sandboxRunner, "sandboxRunner");
    }

    @Override
    public Criterion criterion() {
        return CRITERION;
    }

    @Override
    protected CriterionResult doAnalyze(FileNode projectRoot) {
        Optional<List<String>> command = commandFor(projectRoot);
        if (command.isEmpty()) {
            return CriterionResult.failed(CRITERION,
                    "No pom.xml or package.json found at the project root; nothing to build-check.");
        }

        SandboxResult result = sandboxRunner.run(projectRoot.path(), command.get(), SANDBOX_TIMEOUT);

        if (result.timedOut()) {
            return CriterionResult.failed(CRITERION,
                    "Build check timed out after " + SANDBOX_TIMEOUT.toSeconds() + "s.");
        }
        if (result.exitCode() == SANDBOX_INTERNAL_ERROR_EXIT_CODE) {
            return CriterionResult.failed(CRITERION,
                    "Could not run the build check inside the sandbox: " + summarize(result));
        }
        if (result.exitCode() == 0) {
            return CriterionResult.success(CRITERION, 10.0, "Build manifest validated successfully.");
        }
        return CriterionResult.success(CRITERION, 0.0,
                "Build check failed (exit " + result.exitCode() + "): " + summarize(result));
    }

    /** Root-level only, no recursion: a build manifest belongs at the project root by convention. */
    private static Optional<List<String>> commandFor(FileNode projectRoot) {
        if (hasRootFile(projectRoot, "pom.xml")) {
            return Optional.of(MAVEN_VALIDATE_COMMAND);
        }
        if (hasRootFile(projectRoot, "package.json")) {
            return Optional.of(NODE_MANIFEST_CHECK_COMMAND);
        }
        return Optional.empty();
    }

    private static boolean hasRootFile(FileNode projectRoot, String name) {
        for (FileNode child : projectRoot.children()) {
            if (!child.isDirectory() && child.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String summarize(SandboxResult result) {
        String text = !result.stderr().isBlank() ? result.stderr() : result.stdout();
        text = text.isBlank() ? "(no output)" : text.strip();
        return text.length() <= MAX_OUTPUT_CHARS
                ? text
                : text.substring(0, MAX_OUTPUT_CHARS) + "... (truncated)";
    }
}
