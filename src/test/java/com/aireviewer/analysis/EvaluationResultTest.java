package com.aireviewer.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationResultTest {

    private static final Criterion A_CRITERION =
            new Criterion("naming", "Naming conventions", "Checks identifier naming.", 1.0);
    private static final CriterionResult A_RESULT = CriterionResult.success(A_CRITERION, 8.5, "Mostly consistent.");

    @Test
    void storesItsFields() {
        EvaluationResult result = new EvaluationResult(8.5, List.of(A_RESULT));

        assertEquals(8.5, result.overallScore());
        assertEquals(List.of(A_RESULT), result.criterionResults());
    }

    @Test
    void defensivelyCopiesTheCriterionResultsList() {
        List<CriterionResult> mutable = new ArrayList<>(List.of(A_RESULT));

        EvaluationResult result = new EvaluationResult(8.5, mutable);
        mutable.clear();

        assertEquals(List.of(A_RESULT), result.criterionResults());
    }

    @Test
    void acceptsAnEmptyCriterionResultsList() {
        EvaluationResult result = new EvaluationResult(0.0, List.of());

        assertEquals(List.of(), result.criterionResults());
    }

    @Test
    void rejectsANullCriterionResultsList() {
        assertThrows(NullPointerException.class, () -> new EvaluationResult(8.5, null));
    }

    @Test
    void rejectsANaNOverallScore() {
        assertThrows(IllegalArgumentException.class, () -> new EvaluationResult(Double.NaN, List.of()));
    }
}
