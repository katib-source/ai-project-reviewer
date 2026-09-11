package com.aireviewer;

import com.aireviewer.analysis.AnalysisEngine;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.analyzers.FileSizeStatisticsAnalyzer;
import com.aireviewer.analysis.analyzers.LLMBackedAnalyzer;
import com.aireviewer.analysis.analyzers.NamingConventionAnalyzer;
import com.aireviewer.configuration.AppConfig;
import com.aireviewer.llm.LLMProviderFactory;
import com.aireviewer.llm.LLMSettings;
import com.aireviewer.persistence.AnalysisHistoryStore;
import com.aireviewer.project.ProjectImporter;
import com.aireviewer.report.LatexEscaper;
import com.aireviewer.report.LatexReportCompiler;
import com.aireviewer.application.ReviewService;
import com.aireviewer.web.ApiRouter;
import io.javalin.Javalin;

import java.nio.file.Path;
import java.time.Duration;

/** Composition root: the only place that wires concrete implementations together. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        ProjectImporter importer = new ProjectImporter(config.allowedFileExtensions(), config.maxFileSizeInBytes(), config.ignoredDirectoryNames());
        AnalysisHistoryStore history = new AnalysisHistoryStore(config.historyFilePath());

        LLMSettings llmSettings = LLMSettings.defaults().withKind(config.llmProvider());
        config.mistralApiKey().ifPresent(key -> {
            // Applied below through a local variable because records are immutable.
        });
        if (config.mistralApiKey().isPresent()) {
            llmSettings = llmSettings.withMistral(config.mistralApiKey().orElseThrow(), "");
        }
        var provider = LLMProviderFactory.create(llmSettings);

        AnalysisEngine engine = new AnalysisEngine();
        engine.registerAnalyzer(new FileSizeStatisticsAnalyzer(50_000));
        engine.registerAnalyzer(new NamingConventionAnalyzer());
        engine.registerAnalyzer(new LLMBackedAnalyzer(
                new Criterion("architecture-quality", "Architecture Quality", "Evaluates the architecture and separation of responsibilities of the project.", 1.0),
                provider));

        ReviewService service = new ReviewService(importer, engine, history, new LatexEscaper());
        Path reports = Path.of("reports");
        LatexReportCompiler compiler = new LatexReportCompiler(Duration.ofSeconds(30));

        Javalin app = Javalin.create();
        ApiRouter.register(app, service, reports, compiler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { service.close(); app.stop(); }));
        app.start("127.0.0.1", 7070);
    }
}
