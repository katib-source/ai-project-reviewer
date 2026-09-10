package com.aireviewer.analysis;

import com.aireviewer.project.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Template Method pattern: every {@link Analyzer} needs identical timing, logging and
 * exception-to-failed-result handling, so that one broken analyzer can never crash a whole
 * {@code AnalysisEngine} run. Without this, that guarantee would be a convention every analyzer
 * author has to remember to implement themselves. Here it's structural instead: {@link #analyze}
 * is {@code final} — it owns the fixed skeleton (log start, time the call, invoke the variable
 * step, catch, log the outcome, return) — and a subclass supplies only that variable step by
 * implementing {@link #doAnalyze}, which is free to throw since this class is the one place that
 * catches on its behalf.
 * <p>
 * Only {@link Exception} is caught, deliberately not {@link Throwable}. An {@link Error} (e.g.
 * {@link OutOfMemoryError}, {@link StackOverflowError}) means the JVM itself is in trouble;
 * silently turning one into an ordinary-looking {@link CriterionResult#failed} would hide that.
 * It's allowed to propagate out of {@link #analyze}, breaking that method's usual "never throws"
 * contract in this one documented case — which is exactly the gap {@link AnalysisListener}'s
 * {@code onCriterionError} exists to cover at the {@code AnalysisEngine} level.
 * <p>
 * This class deliberately does not notify an {@link AnalysisListener} itself. Knowing about
 * multiple analyzers and when to fire started/completed/error is orchestration, which belongs to
 * {@code AnalysisEngine}; this class's only job is making one analyzer robust.
 */
public abstract class AbstractAnalyzer implements Analyzer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAnalyzer.class);

    @Override
    public final CriterionResult analyze(FileNode projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Criterion criterion = criterion();
        long startNanos = System.nanoTime();
        LOG.info("Starting analysis for criterion '{}'", criterion.id());
        try {
            CriterionResult result = doAnalyze(projectRoot);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            LOG.info("Completed analysis for criterion '{}' in {}ms (successful={})",
                    criterion.id(), elapsedMs, result.successful());
            return result;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            LOG.warn("Analysis failed for criterion '{}' after {}ms", criterion.id(), elapsedMs, e);
            String reason = "Analyzer threw " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
            return CriterionResult.failed(criterion, reason);
        }
    }

    /**
     * The variable step of the template: evaluates {@code projectRoot} against
     * {@link #criterion()} and returns the outcome. Unlike {@link #analyze}, this method is
     * allowed to throw — including a checked exception such as {@link java.io.IOException} from
     * file I/O, per CLAUDE.md §2 — since {@link #analyze} is what turns any thrown
     * {@link Exception} into a {@link CriterionResult#failed} on every subclass's behalf.
     */
    protected abstract CriterionResult doAnalyze(FileNode projectRoot) throws Exception;
}
