package com.aireviewer.persistence;

import java.util.Objects;

/**
 * One criterion's outcome as stored in history: enough to render a past result (name, score,
 * comment) without carrying the richer, in-memory shape {@code analysis.CriterionResult} will
 * have — this package never imports {@code analysis} (see {@link AnalysisRecord}), so it defines
 * its own minimal projection instead.
 */
public record CriterionSummary(String criterionId, String criterionName, double score, String comment) {

    public CriterionSummary {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(criterionName, "criterionName");
        Objects.requireNonNull(comment, "comment");
        if (criterionId.isBlank()) {
            throw new IllegalArgumentException("criterionId must not be blank");
        }
        if (Double.isNaN(score)) {
            throw new IllegalArgumentException("score must not be NaN");
        }
    }
}
