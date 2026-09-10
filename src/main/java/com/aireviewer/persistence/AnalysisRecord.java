package com.aireviewer.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One completed analysis as stored in history. Deliberately this package's own, smaller shape —
 * not a reuse of {@code analysis}'s in-memory result type. The dependency rule in CLAUDE.md §0
 * makes {@code project}, {@code analysis}, {@code llm}, {@code security}, {@code report} and
 * {@code persistence} siblings under {@code application}, so {@code persistence} may never import
 * {@code analysis}; {@code application} builds one of these from an {@code analysis} result when
 * it calls {@link AnalysisHistoryStore#append}, and that mapping is the only place the two shapes
 * ever meet.
 *
 * @param id               unique identifier assigned before this record is ever stored (by
 *                          whichever caller starts the analysis) — this store never generates one
 * @param projectName      display name of the analyzed project
 * @param projectPath      the imported project's root path, as a string (not {@link java.nio.file.Path})
 *                          so this record round-trips through JSON without a custom serializer
 * @param completedAt      when the analysis finished
 * @param overallScore     the consolidated score across all run criteria
 * @param criterionResults per-criterion outcomes, in the order they were run
 */
public record AnalysisRecord(
        String id,
        String projectName,
        String projectPath,
        Instant completedAt,
        double overallScore,
        List<CriterionSummary> criterionResults
) {

    public AnalysisRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(projectPath, "projectPath");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(criterionResults, "criterionResults");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (Double.isNaN(overallScore)) {
            throw new IllegalArgumentException("overallScore must not be NaN");
        }
        criterionResults = List.copyOf(criterionResults);
    }
}
