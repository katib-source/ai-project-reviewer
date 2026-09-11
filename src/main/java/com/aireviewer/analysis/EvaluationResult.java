package com.aireviewer.analysis;

import java.util.List;
import java.util.Objects;

/**
 * The consolidated outcome of running a selection of {@link Analyzer}s against one project:
 * every {@link CriterionResult} produced, plus the single weighted {@code overallScore}
 * {@code AnalysisEngine} computes from them. Deliberately carries no project identity, id, or
 * timestamp — those belong to {@code persistence.AnalysisRecord}, which {@code application} builds
 * from one of these; duplicating them here would just be a second source of truth for the same
 * facts.
 *
 * @param overallScore     the weighted-average score across {@code criterionResults}
 * @param criterionResults every result from the run, in the order the criteria were run
 */
public record EvaluationResult(double overallScore, List<CriterionResult> criterionResults) {

    public EvaluationResult {
        Objects.requireNonNull(criterionResults, "criterionResults");
        if (Double.isNaN(overallScore)) {
            throw new IllegalArgumentException("overallScore must not be NaN");
        }
        criterionResults = List.copyOf(criterionResults);
    }
}
