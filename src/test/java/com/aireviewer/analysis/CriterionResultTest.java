package com.aireviewer.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriterionResultTest {

    private static final Criterion A_CRITERION =
            new Criterion("naming", "Naming conventions", "Checks identifier naming.", 1.0);

    @Test
    void successBuildsASuccessfulResult() {
        CriterionResult result = CriterionResult.success(A_CRITERION, 8.5, "Mostly consistent.");

        assertTrue(result.successful());
        assertEquals(A_CRITERION, result.criterion());
        assertEquals(8.5, result.score());
        assertEquals("Mostly consistent.", result.comment());
    }

    @Test
    void failedBuildsAZeroScoreResult() {
        CriterionResult result = CriterionResult.failed(A_CRITERION, "Analyzer threw IOException: disk error");

        assertFalse(result.successful());
        assertEquals(A_CRITERION, result.criterion());
        assertEquals(0.0, result.score());
        assertEquals("Analyzer threw IOException: disk error", result.comment());
    }

    @Test
    void rejectsANullCriterion() {
        assertThrows(NullPointerException.class, () -> CriterionResult.success(null, 8.5, "comment"));
    }

    @Test
    void rejectsABlankComment() {
        assertThrows(IllegalArgumentException.class, () -> CriterionResult.success(A_CRITERION, 8.5, " "));
    }

    @Test
    void rejectsANullComment() {
        assertThrows(NullPointerException.class, () -> CriterionResult.failed(A_CRITERION, null));
    }

    @Test
    void rejectsANaNScore() {
        assertThrows(IllegalArgumentException.class,
                () -> CriterionResult.success(A_CRITERION, Double.NaN, "comment"));
    }

    @Test
    void theCanonicalConstructorValidatesTooWhenCalledDirectly() {
        assertThrows(IllegalArgumentException.class,
                () -> new CriterionResult(A_CRITERION, true, 8.5, " "));
    }
}
