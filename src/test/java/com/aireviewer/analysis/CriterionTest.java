package com.aireviewer.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriterionTest {

    @Test
    void storesItsFields() {
        Criterion criterion = new Criterion("naming", "Naming conventions", "Checks identifier naming.", 2.0);

        assertEquals("naming", criterion.id());
        assertEquals("Naming conventions", criterion.name());
        assertEquals("Checks identifier naming.", criterion.description());
        assertEquals(2.0, criterion.weight());
    }

    @Test
    void rejectsABlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Criterion(" ", "Naming conventions", "description", 1.0));
    }

    @Test
    void rejectsABlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Criterion("naming", " ", "description", 1.0));
    }

    @Test
    void rejectsABlankDescription() {
        assertThrows(IllegalArgumentException.class,
                () -> new Criterion("naming", "Naming conventions", " ", 1.0));
    }

    @Test
    void rejectsAZeroWeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new Criterion("naming", "Naming conventions", "description", 0.0));
    }

    @Test
    void rejectsANegativeWeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new Criterion("naming", "Naming conventions", "description", -1.0));
    }

    @Test
    void rejectsANaNWeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new Criterion("naming", "Naming conventions", "description", Double.NaN));
    }

    @Test
    void rejectsANullId() {
        assertThrows(NullPointerException.class,
                () -> new Criterion(null, "Naming conventions", "description", 1.0));
    }
}
