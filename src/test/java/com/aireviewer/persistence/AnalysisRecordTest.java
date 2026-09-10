package com.aireviewer.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisRecordTest {

    private static final CriterionSummary A_CRITERION =
            new CriterionSummary("naming", "Naming conventions", 7.5, "Mostly consistent.");

    private static AnalysisRecord aRecord(List<CriterionSummary> criteria) {
        return new AnalysisRecord("analysis-1", "demo-project", "/tmp/demo-project", Instant.EPOCH, 8.0, criteria);
    }

    @Test
    void storesItsFields() {
        AnalysisRecord record = aRecord(List.of(A_CRITERION));

        assertEquals("analysis-1", record.id());
        assertEquals("demo-project", record.projectName());
        assertEquals("/tmp/demo-project", record.projectPath());
        assertEquals(Instant.EPOCH, record.completedAt());
        assertEquals(8.0, record.overallScore());
        assertEquals(List.of(A_CRITERION), record.criterionResults());
    }

    @Test
    void defensivelyCopiesTheCriterionList() {
        List<CriterionSummary> mutable = new ArrayList<>(List.of(A_CRITERION));

        AnalysisRecord record = aRecord(mutable);
        mutable.clear();

        assertEquals(List.of(A_CRITERION), record.criterionResults());
    }

    @Test
    void rejectsABlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisRecord(" ", "demo-project", "/tmp/demo-project", Instant.EPOCH, 8.0, List.of()));
    }

    @Test
    void rejectsANaNOverallScore() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisRecord("analysis-1", "demo-project", "/tmp/demo-project", Instant.EPOCH,
                        Double.NaN, List.of()));
    }

    @Test
    void rejectsANullCompletedAt() {
        assertThrows(NullPointerException.class,
                () -> new AnalysisRecord("analysis-1", "demo-project", "/tmp/demo-project", null, 8.0, List.of()));
    }
}
