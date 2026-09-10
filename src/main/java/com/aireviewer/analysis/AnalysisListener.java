package com.aireviewer.analysis;

/**
 * Observer pattern: {@code AnalysisEngine} runs an open-ended, configurable selection of
 * {@link Analyzer}s and needs to report progress on each one — today to nothing, soon to an SSE
 * bridge in {@code web} — without ever knowing who, if anyone, is listening. Any UI or transport
 * type that would have to appear here to report progress directly is exactly what {@code analysis}
 * is forbidden from importing (CLAUDE.md §0); Observer is what lets the engine stay ignorant of
 * that and still notify. Adding a new listener (an SSE bridge, a logger, a test spy) is a new
 * implementation of this interface registered with the engine — nothing about the engine changes.
 * <p>
 * Notifications are per criterion, not per whole run: consolidating results across a run produces
 * a value this package doesn't define yet, so a whole-run completion callback isn't part of this
 * contract.
 */
public interface AnalysisListener {

    /** Called when the engine is about to run {@code criterion}. */
    void onCriterionStarted(Criterion criterion);

    /**
     * Called when {@code criterion} finished running, whether it succeeded or not — see
     * {@link CriterionResult#successful()}. An {@link Analyzer} extending {@link AbstractAnalyzer}
     * always reaches this method rather than {@link #onCriterionError}, since its own thrown
     * exceptions are already converted into a {@link CriterionResult#failed} before the engine
     * sees them.
     */
    void onCriterionCompleted(CriterionResult result);

    /**
     * Called when running {@code criterion} could not even produce a {@link CriterionResult} —
     * the engine's last line of defense against an {@link Analyzer} that doesn't extend
     * {@link AbstractAnalyzer} and throws directly, so one broken analyzer still can't be allowed
     * to crash the whole run.
     */
    void onCriterionError(Criterion criterion, Throwable error);
}
