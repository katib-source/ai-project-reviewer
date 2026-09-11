package com.aireviewer.application;

import com.aireviewer.analysis.AnalysisEngine;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.AbstractAnalyzer;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.persistence.AnalysisHistoryStore;
import com.aireviewer.project.FileNode;
import com.aireviewer.project.ProjectImporter;
import com.aireviewer.report.LatexEscaper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ReviewServiceTest {
    @TempDir Path temp;

    @Test void facadeImportsListsCriteriaStartsAsyncAnalysisAndStoresResult() throws Exception {
        Path project = Files.createDirectory(temp.resolve("demo"));
        Files.writeString(project.resolve("Main.java"), "class Main {}\n");
        AnalysisEngine engine = new AnalysisEngine();
        Criterion criterion = new Criterion("tests", "Tests", "Test quality", 1.0);
        engine.registerAnalyzer(new AbstractAnalyzer() {
            @Override public Criterion criterion() { return criterion; }
            @Override protected CriterionResult doAnalyze(FileNode root) { return CriterionResult.success(criterion, 8.0, "Good"); }
        });
        ReviewService service = new ReviewService(
                new ProjectImporter(Set.of("java"), 1_000_000, Set.of("target")), engine,
                new AnalysisHistoryStore(temp.resolve("history.json")), new LatexEscaper(),
                Executors.newVirtualThreadPerTaskExecutor());
        try {
            ReviewService.ImportedProject imported = service.importProject(project);
            assertFalse(imported.projectId().isBlank());
            assertEquals("Tests", service.listCriteria().getFirst().name());
            String analysisId = service.startAnalysis(imported.projectId(), List.of("tests"));
            for (int i = 0; i < 50 && service.findResult(analysisId).isEmpty(); i++) Thread.sleep(20);
            assertTrue(service.findResult(analysisId).isPresent());
            assertEquals(8.0, service.findResult(analysisId).orElseThrow().overallScore());
            assertEquals(1, service.listHistory().size());
        } finally { service.close(); }
    }
}
