package com.aireviewer.web;
import com.aireviewer.application.ReviewService;
import com.aireviewer.persistence.AnalysisRecord;
import com.aireviewer.persistence.PersistenceException;
import com.aireviewer.web.dto.*;
import io.javalin.http.Context;
public final class HistoryController {
    private final ReviewService service;
    public HistoryController(ReviewService service) { this.service = service; }
    public void list(Context ctx) {
        try { ctx.json(service.listHistory().stream().map(HistoryController::toDto).toList()); }
        catch (PersistenceException e) { ctx.status(500).json(new ErrorResponse("Unable to read analysis history")); }
    }
    private static AnalysisHistoryItemDto toDto(AnalysisRecord r) { return new AnalysisHistoryItemDto(r.id(), r.projectPath(), r.projectName(), r.overallScore(), r.completedAt()); }
}
