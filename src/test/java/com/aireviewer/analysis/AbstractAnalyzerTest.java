package com.aireviewer.analysis;

import com.aireviewer.project.FileNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractAnalyzerTest {

    private static final Criterion A_CRITERION =
            new Criterion("naming", "Naming conventions", "Checks identifier naming.", 1.0);

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
    void passesThroughASuccessfulResultUnchanged() {
        CriterionResult expected = CriterionResult.success(A_CRITERION, 9.0, "Looks good.");
        AbstractAnalyzer analyzer = analyzerReturning(expected);

        assertSame(expected, analyzer.analyze(A_PROJECT_ROOT));
    }

    @Test
    void convertsAThrownExceptionIntoAFailedResult() {
        AbstractAnalyzer analyzer = analyzerThrowing(new IllegalStateException("disk error"));

        CriterionResult result = analyzer.analyze(A_PROJECT_ROOT);

        assertFalse(result.successful());
        assertEquals(A_CRITERION, result.criterion());
        assertEquals(0.0, result.score());
        assertTrue(result.comment().contains("IllegalStateException"));
        assertTrue(result.comment().contains("disk error"));
    }

    @Test
    void rejectsANullProjectRoot() {
        AbstractAnalyzer analyzer = analyzerReturning(CriterionResult.success(A_CRITERION, 1.0, "n/a"));

        assertThrows(NullPointerException.class, () -> analyzer.analyze(null));
    }

    @Test
    void letsAnErrorPropagateRatherThanSwallowingIt() {
        AbstractAnalyzer analyzer = new AbstractAnalyzer() {
            @Override
            public Criterion criterion() {
                return A_CRITERION;
            }

            @Override
            protected CriterionResult doAnalyze(FileNode projectRoot) {
                throw new StackOverflowError("simulated");
            }
        };

        assertThrows(StackOverflowError.class, () -> analyzer.analyze(A_PROJECT_ROOT));
    }

    private static AbstractAnalyzer analyzerReturning(CriterionResult result) {
        return new AbstractAnalyzer() {
            @Override
            public Criterion criterion() {
                return A_CRITERION;
            }

            @Override
            protected CriterionResult doAnalyze(FileNode projectRoot) {
                return result;
            }
        };
    }

    private static AbstractAnalyzer analyzerThrowing(RuntimeException exception) {
        return new AbstractAnalyzer() {
            @Override
            public Criterion criterion() {
                return A_CRITERION;
            }

            @Override
            protected CriterionResult doAnalyze(FileNode projectRoot) {
                throw exception;
            }
        };
    }
}
