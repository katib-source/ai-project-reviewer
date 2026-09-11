package com.aireviewer.web;

import com.aireviewer.application.ReviewService;
import com.aireviewer.report.LatexReportCompiler;
import com.aireviewer.web.dto.ErrorResponse;
import com.aireviewer.web.dto.ReportResponse;
import io.javalin.http.Context;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Thin adapter for report generation and file download. */
public final class ReportController {
    private final ReviewService service;
    private final Path reportDirectory;
    private final LatexReportCompiler compiler;
    public ReportController(ReviewService service, Path reportDirectory, LatexReportCompiler compiler) { this.service = service; this.reportDirectory = reportDirectory.toAbsolutePath().normalize(); this.compiler = compiler; }
    public void generate(Context ctx) {
        try {
            ReviewService.ReportFiles files = service.generateReport(ctx.pathParam("id"), reportDirectory);
            String pdfUrl = "";
            try {
                Path pdf = compiler.compile(files.texFile());
                if (Files.isRegularFile(pdf)) pdfUrl = "/reports/" + pdf.getFileName();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (IOException ignored) { }
            ctx.json(new ReportResponse("/reports/" + files.texFile().getFileName(), pdfUrl));
        } catch (IOException e) { ctx.status(404).json(new ErrorResponse(e.getMessage())); }
    }
    public void download(Context ctx) {
        String name = Path.of(ctx.pathParam("name")).getFileName().toString();
        Path file = reportDirectory.resolve(name).normalize();
        if (!file.startsWith(reportDirectory) || !Files.isRegularFile(file)) { ctx.status(404).json(new ErrorResponse("Report not found")); return; }
        try { ctx.contentType(name.endsWith(".pdf") ? "application/pdf" : "application/x-tex"); ctx.result(Files.readAllBytes(file)); }
        catch (IOException e) { ctx.status(500).json(new ErrorResponse("Unable to read report")); }
    }
}
