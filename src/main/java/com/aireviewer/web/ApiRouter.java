package com.aireviewer.web;
import com.aireviewer.application.ReviewService;
import com.aireviewer.report.LatexReportCompiler;
import io.javalin.Javalin;
import java.nio.file.Path;
import java.util.Objects;
public final class ApiRouter {
    private ApiRouter() { }
    public static void register(Javalin app, ReviewService service, Path reportDirectory, LatexReportCompiler compiler) {
        Objects.requireNonNull(app); Objects.requireNonNull(service); Objects.requireNonNull(reportDirectory); Objects.requireNonNull(compiler);
        ProjectController projects = new ProjectController(service); CriteriaController criteria = new CriteriaController(service); AnalysisController analyses = new AnalysisController(service); SseController sse = new SseController(service); ReportController reports = new ReportController(service, reportDirectory, compiler); HistoryController history = new HistoryController(service);
        app.post("/api/projects", projects::importProject);
        app.post("/api/projects/upload", projects::uploadProject);
        app.get("/api/criteria", criteria::list);
        app.post("/api/analyses", analyses::start);
        app.sse("/api/analyses/{id}/events", sse::stream);
        app.get("/api/analyses/{id}", analyses::get);
        app.post("/api/analyses/{id}/report", reports::generate);
        app.get("/api/analyses", history::list);
        app.get("/reports/{name}", reports::download);
    }
}
