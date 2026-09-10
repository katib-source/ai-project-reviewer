package com.aireviewer.analysis;

import com.aireviewer.project.FileNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisEngineTest {

    private static final Criterion NAMING = new Criterion("naming", "Naming", "Checks naming.", 1.0);
    private static final Criterion STYLE = new Criterion("style", "Style", "Checks style.", 3.0);

    private static final FileNode A_PROJECT_ROOT = new FileNode() {
        @Override
        public String name() {
            return "project";
        }

        @Override
        public Path path() {
            return Path.of(".");
        }

        @Override
        public long sizeInBytes() {
            return 0;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public List<FileNode> children() {
            return List.of();
        }
    };

    @Test
    void consolidatesAWeightedAverageAcrossResults() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 10.0, "Great.")));
        engine.registerAnalyzer(fixedResultAnalyzer(STYLE, CriterionResult.success(STYLE, 2.0, "Meh.")));

        EvaluationResult result = engine.run(A_PROJECT_ROOT, Set.of("naming", "style"), List.of());

        assertEquals(4.0, result.overallScore(), 1e-9);
        assertEquals(2, result.criterionResults().size());
    }

    @Test
    void runsCriteriaInRegistrationOrderRegardlessOfSetIterationOrder() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(fixedResultAnalyzer(STYLE, CriterionResult.success(STYLE, 5.0, "ok")));
        engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 7.0, "ok")));

        EvaluationResult result = engine.run(A_PROJECT_ROOT, Set.of("naming", "style"), List.of());

        assertEquals("style", result.criterionResults().get(0).criterion().id());
        assertEquals("naming", result.criterionResults().get(1).criterion().id());
    }

    @Test
    void notifiesListenersInStartedThenCompletedOrderPerCriterion() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 8.0, "ok")));
        engine.registerAnalyzer(fixedResultAnalyzer(STYLE, CriterionResult.success(STYLE, 6.0, "ok")));
        RecordingListener listener = new RecordingListener();

        engine.run(A_PROJECT_ROOT, Set.of("naming", "style"), List.of(listener));

        assertEquals(List.of(
                "started:naming", "completed:naming",
                "started:style", "completed:style"
        ), listener.events);
    }

    @Test
    void convertsADirectlyThrownExceptionIntoAFailedResultAndNotifiesError() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(throwingAnalyzer(NAMING, new IllegalStateException("boom")));
        RecordingListener listener = new RecordingListener();

        EvaluationResult result = engine.run(A_PROJECT_ROOT, Set.of("naming"), List.of(listener));

        assertEquals(1, result.criterionResults().size());
        CriterionResult naming = result.criterionResults().get(0);
        assertFalse(naming.successful());
        assertEquals(0.0, naming.score());
        assertEquals(List.of("started:naming", "error:naming"), listener.events);
    }

    @Test
    void letsAnErrorPropagateOutOfRun() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(new Analyzer() {
            @Override
            public Criterion criterion() {
                return NAMING;
            }

            @Override
            public CriterionResult analyze(FileNode projectRoot) {
                throw new StackOverflowError("simulated");
            }
        });

        assertThrows(StackOverflowError.class,
                () -> engine.run(A_PROJECT_ROOT, Set.of("naming"), List.of()));
    }

    @Test
    void rejectsRegisteringTwoAnalyzersForTheSameCriterionId() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 5.0, "ok")));

        assertThrows(IllegalStateException.class,
                () -> engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 1.0, "ok"))));
    }

    @Test
    void rejectsAnEmptyCriterionSelection() {
        AnalysisEngine engine = new AnalysisEngine();

        assertThrows(IllegalArgumentException.class,
                () -> engine.run(A_PROJECT_ROOT, Set.of(), List.of()));
    }

    @Test
    void rejectsAnUnknownCriterionId() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 5.0, "ok")));

        assertThrows(IllegalArgumentException.class,
                () -> engine.run(A_PROJECT_ROOT, Set.of("naming", "unknown"), List.of()));
    }

    @Test
    void availableCriteriaReflectsRegistrationOrder() {
        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(fixedResultAnalyzer(STYLE, CriterionResult.success(STYLE, 5.0, "ok")));
        engine.registerAnalyzer(fixedResultAnalyzer(NAMING, CriterionResult.success(NAMING, 5.0, "ok")));

        assertEquals(List.of(STYLE, NAMING), engine.availableCriteria());
    }

    private static Analyzer fixedResultAnalyzer(Criterion criterion, CriterionResult result) {
        return new Analyzer() {
            @Override
            public Criterion criterion() {
                return criterion;
            }

            @Override
            public CriterionResult analyze(FileNode projectRoot) {
                return result;
            }
        };
    }

    private static Analyzer throwingAnalyzer(Criterion criterion, RuntimeException exception) {
        return new Analyzer() {
            @Override
            public Criterion criterion() {
                return criterion;
            }

            @Override
            public CriterionResult analyze(FileNode projectRoot) {
                throw exception;
            }
        };
    }

    private static final class RecordingListener implements AnalysisListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onCriterionStarted(Criterion criterion) {
            events.add("started:" + criterion.id());
        }

        @Override
        public void onCriterionCompleted(CriterionResult result) {
            events.add("completed:" + result.criterion().id());
        }

        @Override
        public void onCriterionError(Criterion criterion, Throwable error) {
            events.add("error:" + criterion.id());
        }
    }
}
