package com.aireviewer.web;

import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.analysis.EvaluationResult;
import com.aireviewer.application.ReviewService;
import com.aireviewer.web.dto.*;
import io.javalin.http.Context;

/** Thin adapter for analysis start and result endpoints. */
public final class AnalysisController {
    private final ReviewService service;
    public AnalysisController(ReviewService service) { this.service = service; }
    public void start(Context ctx) {
        StartAnalysisRequest request = ctx.bodyAsClass(StartAnalysisRequest.class);
        if (request.projectId() == null || request.projectId().isBlank()) { ctx.status(400).json(new ErrorResponse("projectId is required")); return; }
        if (request.criterionIds().isEmpty()) { ctx.status(400).json(new ErrorResponse("criterionIds must not be empty")); return; }
        try { ctx.status(202).json(new StartAnalysisResponse(service.startAnalysis(request.projectId(), request.criterionIds()))); }
        catch (IllegalArgumentException e) { ctx.status(400).json(new ErrorResponse(e.getMessage())); }
    }
    public void get(Context ctx) {
        String id = ctx.pathParam("id");
        service.findResult(id).map(result -> toDto(id, result)).ifPresentOrElse(
                value -> ctx.json(value), () -> ctx.status(404).json(new ErrorResponse("Analysis not found: " + id)));
    }
    private static AnalysisResultResponse toDto(String id, EvaluationResult result) {
        return new AnalysisResultResponse(id, result.overallScore(), result.criterionResults().stream().map(AnalysisController::criterion).toList());
    }
    private static CriterionResultDto criterion(CriterionResult result) {
        return new CriterionResultDto(result.criterion().id(), result.criterion().name(), result.successful(), result.score(), result.comment());
    }
}
