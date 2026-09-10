package com.aireviewer.analysis;

import com.aireviewer.project.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Registry, runner and consolidator for {@link Analyzer}s. Not itself an implementation of one of
 * the nine patterns in {@code docs/TEAM_BRIEF.md} §4 — it's the concrete class that <em>uses</em>
 * Strategy ({@link Analyzer}) and notifies Observer ({@link AnalysisListener}); consolidation is a
 * plain weighted average, not a new abstraction, since CLAUDE.md §8 forbids adding a pattern that
 * doesn't solve a problem this package actually has.
 * <p>
 * Registration ({@link #registerAnalyzer}) happens once, at composition-root time in
 * {@code Main}. {@link #run} is called per analysis request; it is plain sequential and
 * synchronous — the asynchronous "start now, stream progress, fetch later" behaviour described in
 * the API contract is {@code application}'s responsibility, not this class's.
 */
public final class AnalysisEngine {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisEngine.class);

    private final Map<String, Analyzer> analyzersByCriterionId = new LinkedHashMap<>();

    /**
     * Registers {@code analyzer} under its own {@link Criterion#id()}. Meant to be called once
     * per analyzer at startup, never mid-run.
     *
     * @throws IllegalStateException if an analyzer is already registered for that criterion id —
     *                                two analyzers sharing an id is a composition-root wiring bug,
     *                                not a runtime condition to recover from
     */
    public void registerAnalyzer(Analyzer analyzer) {
        Objects.requireNonNull(analyzer, "analyzer");
        String id = analyzer.criterion().id();
        if (analyzersByCriterionId.containsKey(id)) {
            throw new IllegalStateException("An analyzer is already registered for criterion id: " + id);
        }
        analyzersByCriterionId.put(id, analyzer);
        LOG.info("Registered analyzer for criterion '{}'", id);
    }

    /**
     * Every currently registered criterion, in registration order. Backs
     * {@code GET /api/criteria} (via {@code application.ReviewService}) and is also what
     * {@link #run} validates requested criterion ids against.
     */
    public List<Criterion> availableCriteria() {
        List<Criterion> criteria = new ArrayList<>(analyzersByCriterionId.size());
        for (Analyzer analyzer : analyzersByCriterionId.values()) {
            criteria.add(analyzer.criterion());
        }
        return List.copyOf(criteria);
    }

    /**
     * Runs the analyzer registered for each id in {@code criterionIds} against
     * {@code projectRoot}, notifying every listener in {@code listeners} as each criterion starts
     * and finishes, and consolidates the results into one {@link EvaluationResult}.
     * <p>
     * Criteria run in registration order (the order they were passed to
     * {@link #registerAnalyzer}), restricted to the requested ids — {@code criterionIds} is a
     * {@link Set} precisely because running the same criterion twice is meaningless.
     * <p>
     * A well-behaved {@link Analyzer} (one extending {@link AbstractAnalyzer}) never throws —
     * its failures already arrive as a {@link CriterionResult#failed}. This method's own
     * {@code catch (Exception e)} is the last line of defense for one that doesn't: {@code
     * criterion} still gets a {@link CriterionResult#failed} entry in the returned result (so the
     * consolidated score stays well-defined), but listeners are told via
     * {@link AnalysisListener#onCriterionError} instead of
     * {@link AnalysisListener#onCriterionCompleted}, so the SSE/UI layer can tell "ran and scored
     * badly" apart from "couldn't be run at all." An {@link Error} is not caught here either, for
     * the same reason {@link AbstractAnalyzer} doesn't catch one: it means the JVM itself is in
     * trouble, and hiding that behind a per-criterion failure would be worse than letting it
     * propagate.
     *
     * @throws IllegalArgumentException if {@code criterionIds} is empty, or contains an id with
     *                                   no registered analyzer — consolidating zero results has
     *                                   no well-defined weighted average, and running an unknown
     *                                   criterion is a caller contract violation, not a
     *                                   per-criterion failure
     */
    public EvaluationResult run(FileNode projectRoot, Set<String> criterionIds, List<AnalysisListener> listeners) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(criterionIds, "criterionIds");
        Objects.requireNonNull(listeners, "listeners");
        if (criterionIds.isEmpty()) {
            throw new IllegalArgumentException("criterionIds must not be empty");
        }
        for (String id : criterionIds) {
            if (!analyzersByCriterionId.containsKey(id)) {
                throw new IllegalArgumentException("No analyzer registered for criterion id: " + id);
            }
        }

        List<Analyzer> selected = new ArrayList<>(criterionIds.size());
        for (Analyzer analyzer : analyzersByCriterionId.values()) {
            if (criterionIds.contains(analyzer.criterion().id())) {
                selected.add(analyzer);
            }
        }

        List<CriterionResult> results = new ArrayList<>(selected.size());
        for (Analyzer analyzer : selected) {
            Criterion criterion = analyzer.criterion();
            notify(listeners, listener -> listener.onCriterionStarted(criterion));
            try {
                CriterionResult result = analyzer.analyze(projectRoot);
                notify(listeners, listener -> listener.onCriterionCompleted(result));
                results.add(result);
            } catch (Exception e) {
                LOG.warn("Analyzer for criterion '{}' threw directly instead of returning a failed result",
                        criterion.id(), e);
                notify(listeners, listener -> listener.onCriterionError(criterion, e));
                String reason = "Analyzer threw " + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : "");
                results.add(CriterionResult.failed(criterion, reason));
            }
        }

        double weightedSum = 0.0;
        double weightSum = 0.0;
        for (CriterionResult result : results) {
            double weight = result.criterion().weight();
            weightedSum += result.score() * weight;
            weightSum += weight;
        }

        return new EvaluationResult(weightedSum / weightSum, results);
    }

    private static void notify(List<AnalysisListener> listeners, Consumer<AnalysisListener> notification) {
        for (AnalysisListener listener : listeners) {
            notification.accept(listener);
        }
    }
}
