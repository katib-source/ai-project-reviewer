package com.aireviewer.application;

import com.aireviewer.analysis.AnalysisEngine;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.AbstractAnalyzer;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.persistence.AnalysisHistoryStore;
import com.aireviewer.project.FileNode;
import com.aireviewer.project.ProjectImporter;
import com.aireviewer.project.UploadedProjectWriter;
import com.aireviewer.report.LatexEscaper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
                new UploadedProjectWriter(temp.resolve("uploads"), 100, 1_000_000),
                Executors.newVirtualThreadPerTaskExecutor());
        try {
            ReviewService.ImportedProject imported = service.importProject(project);
            assertFalse(imported.projectId().isBlank());
            assertEquals("Tests", service.listCriteria().getFirst().name());
            String analysisId = service.startAnalysis(imported.projectId(), List.of("tests"));
            CountDownLatch completed = new CountDownLatch(1);
            service.subscribe(analysisId, event -> {
                if (event instanceof AnalysisEvent.AnalysisCompleted) {
                    completed.countDown();
                }
            });
            assertTrue(completed.await(5, TimeUnit.SECONDS), "analysis did not complete in time");
            assertTrue(service.findResult(analysisId).isPresent());
            assertEquals(8.0, service.findResult(analysisId).orElseThrow().overallScore());
            assertEquals(1, service.listHistory().size());
        } finally { service.close(); }
    }

    @Test void uploadedProjectIsImportedLikeALocalPathAndDeletedOnClose() throws Exception {
        Path uploads = temp.resolve("uploads");
        ReviewService service = new ReviewService(
                new ProjectImporter(Set.of("java"), 1_000_000, Set.of("target")), new AnalysisEngine(),
                new AnalysisHistoryStore(temp.resolve("history.json")), new LatexEscaper(),
                new UploadedProjectWriter(uploads, 100, 1_000_000),
                Executors.newVirtualThreadPerTaskExecutor());
        try {
            ReviewService.ImportedProject imported = service.importUploadedProject(List.of(
                    new ReviewService.UploadedFile("demo/src/Main.java",
                            () -> new ByteArrayInputStream("class Main {}".getBytes(StandardCharsets.UTF_8)))));

            assertEquals("demo", imported.tree().name());
            FileNode src = imported.tree().children().getFirst();
            assertEquals("Main.java", src.children().getFirst().name());
        } finally { service.close(); }

        try (Stream<Path> left = Files.list(uploads)) {
            assertEquals(0, left.count(), "close() must delete uploaded copies");
        }
    }
}
