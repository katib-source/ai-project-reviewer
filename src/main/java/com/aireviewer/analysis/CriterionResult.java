package com.aireviewer.analysis;

import java.util.Objects;

/**
 * The outcome of running one {@link Analyzer} against its {@link Criterion}. Meant to be built
 * through {@link #success} or {@link #failed} rather than the canonical constructor directly —
 * those two names make which of the only two valid shapes a result can take explicit at the call
 * site, and are what {@link AbstractAnalyzer} and every {@link Analyzer} actually use. The
 * canonical constructor stays {@code public} (Java requires a record's canonical constructor to be
 * at least as accessible as the record type itself, and this type is used well outside this
 * package), so it still validates every field regardless of which path a caller takes — it just
 * can't be hidden behind the factories entirely.
 *
 * @param criterion  the criterion this result came from
 * @param successful whether the analyzer completed normally
 * @param score      this criterion's score; {@code 0.0} on a failed result — a failed criterion
 *                   counts as a zero contribution to the consolidated score rather than being
 *                   silently excluded from it, so callers can sum {@code score * weight} without
 *                   special-casing failures
 * @param comment    explanation of the score on success, or the failure reason on failure —
 *                   always present, never blank
 */
public record CriterionResult(Criterion criterion, boolean successful, double score, String comment) {

    public CriterionResult {
        Objects.requireNonNull(criterion, "criterion");
        Objects.requireNonNull(comment, "comment");
        if (comment.isBlank()) {
            throw new IllegalArgumentException("comment must not be blank");
        }
        if (Double.isNaN(score)) {
            throw new IllegalArgumentException("score must not be NaN");
        }
    }

    /**
     * A successful result: the analyzer ran to completion and produced a score.
     */
    public static CriterionResult success(Criterion criterion, double score, String comment) {
        return new CriterionResult(criterion, true, score, comment);
    }

    /**
     * A failed result: the analyzer could not produce a score. {@code reason} should explain what
     * went wrong (e.g. an exception's message) in terms meaningful outside the analyzer's own code.
     */
    public static CriterionResult failed(Criterion criterion, String reason) {
        return new CriterionResult(criterion, false, 0.0, reason);
    }
}
