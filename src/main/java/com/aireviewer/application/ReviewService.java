package com.aireviewer.application;

import com.aireviewer.analysis.AnalysisEngine;
import com.aireviewer.analysis.AnalysisListener;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.analysis.EvaluationResult;
import com.aireviewer.persistence.AnalysisHistoryStore;
import com.aireviewer.persistence.AnalysisRecord;
import com.aireviewer.persistence.CriterionSummary;
import com.aireviewer.persistence.PersistenceException;
import com.aireviewer.project.FileNode;
import com.aireviewer.project.ProjectImportException;
import com.aireviewer.project.ProjectImporter;
import com.aireviewer.project.UploadedProjectFile;
import com.aireviewer.project.UploadedProjectWriter;
import com.aireviewer.report.LatexEscaper;
import com.aireviewer.report.LatexReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Facade pattern: the web layer depends on one application entry point while this class coordinates
 * project import, analysis, persistence and report generation.
 */
public final class ReviewService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);

    /** Bound on how long {@link #close()} waits for an in-flight analysis to finish before forcing shutdown. */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final ProjectImporter projectImporter;
    private final AnalysisEngine analysisEngine;
    private final AnalysisHistoryStore history;
    private final LatexEscaper escaper;
    private final UploadedProjectWriter uploads;
    private final ExecutorService executor;
    private final Map<String, ImportedProject> projects = new ConcurrentHashMap<>();
    private final Map<String, EvaluationResult> results = new ConcurrentHashMap<>();
    private final Map<String, List<AnalysisEvent>> eventHistory = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<AnalysisEvent>>> subscribers = new ConcurrentHashMap<>();

    public ReviewService(ProjectImporter projectImporter,
                         AnalysisEngine analysisEngine,
                         AnalysisHistoryStore history,
                         LatexEscaper escaper,
                         UploadedProjectWriter uploads) {
        this(projectImporter, analysisEngine, history, escaper, uploads,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    ReviewService(ProjectImporter projectImporter,
                  AnalysisEngine analysisEngine,
                  AnalysisHistoryStore history,
                  LatexEscaper escaper,
                  UploadedProjectWriter uploads,
                  ExecutorService executor) {
        this.projectImporter = Objects.requireNonNull(projectImporter, "projectImporter");
        this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
        this.history = Objects.requireNonNull(history, "history");
        this.escaper = Objects.requireNonNull(escaper, "escaper");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public ImportedProject importProject(Path path) throws ProjectImportException {
        Objects.requireNonNull(path, "path");
        FileNode tree = projectImporter.importProject(path);
        String projectId = UUID.randomUUID().toString();
        ImportedProject project = new ImportedProject(projectId, tree);
        projects.put(projectId, project);
        return project;
    }

    /**
     * Imports a project uploaded from the browser's folder picker: the files are stored in a
     * private temporary directory, then imported exactly like a local path.
     */
    public ImportedProject importUploadedProject(List<UploadedFile> files) throws ProjectImportException {
        Objects.requireNonNull(files, "files");
        List<UploadedProjectFile> projectFiles = files.stream()
                .map(file -> new UploadedProjectFile(file.relativePath(), file.content()))
                .toList();
        return importProject(uploads.write(projectFiles));
    }

    public List<Criterion> listCriteria() {
        return analysisEngine.availableCriteria();
    }

    /** Starts an analysis asynchronously and immediately returns its id. */
    public String startAnalysis(String projectId, List<String> criterionIds) {
        ImportedProject project = requireProject(projectId);
        Objects.requireNonNull(criterionIds, "criterionIds");
        if (criterionIds.isEmpty()) {
            throw new IllegalArgumentException("criterionIds must not be empty");
        }
        Set<String> ids = Set.copyOf(criterionIds);
        String analysisId = UUID.randomUUID().toString();
        eventHistory.putIfAbsent(analysisId, new CopyOnWriteArrayList<>());
        subscribers.putIfAbsent(analysisId, new CopyOnWriteArrayList<>());

        executor.submit(() -> runAnalysis(analysisId, project, ids));
        return analysisId;
    }

    private void runAnalysis(String analysisId, ImportedProject project, Set<String> criterionIds) {
        AnalysisListener listener = new AnalysisListener() {
            @Override
            public void onCriterionStarted(Criterion criterion) {
                publish(new AnalysisEvent.CriterionStarted(analysisId, criterion.id(), criterion.name()));
            }

            @Override
            public void onCriterionCompleted(CriterionResult result) {
                publish(new AnalysisEvent.CriterionCompleted(analysisId, result));
            }

            @Override
            public void onCriterionError(Criterion criterion, Throwable error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage();
                publish(new AnalysisEvent.Error(analysisId,
                        "Criterion '" + criterion.id() + "' failed: " + message));
            }
        };

        try {
            EvaluationResult evaluation = analysisEngine.run(project.tree(), criterionIds, List.of(listener));
            // Persist before publishing: once findResult() sees an analysis, its history entry
            // (which generateReport() reads) must already exist.
            persistCompletedResult(analysisId, project, evaluation);
            results.put(analysisId, evaluation);
            publish(new AnalysisEvent.AnalysisCompleted(analysisId, evaluation.overallScore()));
        } catch (Exception e) {
            LOG.error("Analysis {} failed", analysisId, e);
            publish(new AnalysisEvent.Error(analysisId,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Replays already observed events, then subscribes to future events. */
    public AutoCloseable subscribe(String analysisId, Consumer<AnalysisEvent> listener) {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(listener, "listener");
        List<AnalysisEvent> historyForAnalysis = eventHistory.get(analysisId);
        if (historyForAnalysis == null) {
            throw new IllegalArgumentException("Unknown analysisId: " + analysisId);
        }
        for (AnalysisEvent event : historyForAnalysis) {
            listener.accept(event);
        }
        CopyOnWriteArrayList<Consumer<AnalysisEvent>> list =
                subscribers.computeIfAbsent(analysisId, ignored -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return () -> list.remove(listener);
    }

    public Optional<EvaluationResult> findResult(String analysisId) {
        Objects.requireNonNull(analysisId, "analysisId");
        return Optional.ofNullable(results.get(analysisId));
    }

    public List<AnalysisRecord> listHistory() throws PersistenceException {
        return history.listAll();
    }

    public ReportFiles generateReport(String analysisId, Path outputDirectory) throws IOException {
        EvaluationResult result = findResult(analysisId)
                .orElseThrow(() -> new IOException("Analysis not found or not completed: " + analysisId));
        AnalysisRecord record;
        try {
            record = history.findById(analysisId)
                    .orElseThrow(() -> new IOException("Analysis history not found: " + analysisId));
        } catch (PersistenceException e) {
            throw new IOException("Unable to read analysis history", e);
        }

        Files.createDirectories(outputDirectory);
        String safeId = analysisId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path texFile = outputDirectory.resolve("evaluation-" + safeId + ".tex");
        LatexReportBuilder builder = new LatexReportBuilder(escaper)
                .title("AI Project Reviewer - Evaluation")
                .project(record.projectName(), record.projectPath())
                .overallScore(result.overallScore());

        for (CriterionResult criterion : result.criterionResults()) {
            builder.addCriterion(
                    criterion.criterion().id(),
                    criterion.criterion().name(),
                    criterion.score(),
                    criterion.comment());
        }
        Files.writeString(texFile, builder.build(), StandardCharsets.UTF_8);
        return new ReportFiles(texFile, outputDirectory.resolve("evaluation-" + safeId + ".pdf"));
    }

    private void publish(AnalysisEvent event) {
        eventHistory.computeIfAbsent(event.analysisId(), ignored -> new CopyOnWriteArrayList<>()).add(event);
        for (Consumer<AnalysisEvent> subscriber :
                subscribers.getOrDefault(event.analysisId(), new CopyOnWriteArrayList<>())) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException e) {
                LOG.debug("Analysis event subscriber failed", e);
            }
        }
    }

    private void persistCompletedResult(String analysisId, ImportedProject project, EvaluationResult evaluation) {
        List<CriterionSummary> summaries = new ArrayList<>();
        for (CriterionResult result : evaluation.criterionResults()) {
            summaries.add(new CriterionSummary(
                    result.criterion().id(),
                    result.criterion().name(),
                    result.score(),
                    result.comment()));
        }
        try {
            history.append(new AnalysisRecord(
                    analysisId,
                    project.tree().name(),
                    project.tree().path().toString(),
                    Instant.now(),
                    evaluation.overallScore(),
                    summaries));
        } catch (PersistenceException e) {
            LOG.error("Could not persist completed analysis {}", analysisId, e);
        }
    }

    private ImportedProject requireProject(String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        ImportedProject project = projects.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Unknown projectId: " + projectId);
        }
        return project;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("Executor did not terminate within {}s; forcing shutdown", SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Only once no analysis can still be reading them.
        uploads.close();
    }

    public record ImportedProject(String projectId, FileNode tree) { }
    /** One uploaded file: its path relative to the chosen folder, and a way to open its content. */
    public record UploadedFile(String relativePath, Supplier<InputStream> content) { }
    public record ReportFiles(Path texFile, Path pdfFile) { }
}
