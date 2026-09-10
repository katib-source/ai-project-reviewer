package com.aireviewer.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriterionSummaryTest {

    @Test
    void storesItsFields() {
        CriterionSummary summary = new CriterionSummary("naming", "Naming conventions", 7.5, "Mostly consistent.");

        assertEquals("naming", summary.criterionId());
        assertEquals("Naming conventions", summary.criterionName());
        assertEquals(7.5, summary.score());
        assertEquals("Mostly consistent.", summary.comment());
    }

    @Test
    void rejectsABlankCriterionId() {
        assertThrows(IllegalArgumentException.class,
                () -> new CriterionSummary(" ", "Naming conventions", 7.5, "comment"));
    }

    @Test
    void rejectsANaNScore() {
        assertThrows(IllegalArgumentException.class,
                () -> new CriterionSummary("naming", "Naming conventions", Double.NaN, "comment"));
    }

    @Test
    void rejectsANullCriterionName() {
        assertThrows(NullPointerException.class,
                () -> new CriterionSummary("naming", null, 7.5, "comment"));
    }
}
