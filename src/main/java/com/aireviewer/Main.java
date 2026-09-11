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

        var provider = LLMProviderFactory.create(llmSettings(config));

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

    /**
     * Maps configuration onto LLM settings. Every configured key is carried over, and
     * {@code LLM_PROVIDER} alone decides which provider is used: switching between Mistral and
     * Groq is a one-line config change, never a code change. A key missing for the selected
     * provider is reported by the factory at startup, naming the variable to set.
     */
    static LLMSettings llmSettings(AppConfig config) {
        LLMSettings settings = LLMSettings.defaults();
        if (config.mistralApiKey().isPresent()) {
            settings = settings.withMistral(config.mistralApiKey().orElseThrow(), "");
        }
        if (config.groqApiKey().isPresent()) {
            settings = settings.withGroq(config.groqApiKey().orElseThrow(), "");
        }
        // Each with*() above also selects its own kind, so the configured kind is applied last.
        return settings.withKind(config.llmProvider());
    }
}
