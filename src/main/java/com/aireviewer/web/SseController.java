package com.aireviewer.web;

import com.aireviewer.application.AnalysisEvent;
import com.aireviewer.application.ReviewService;
import com.aireviewer.web.dto.*;
import io.javalin.http.sse.SseClient;

/** Bridges application events to browser Server-Sent Events. */
public final class SseController {
    private final ReviewService service;
    public SseController(ReviewService service) { this.service = service; }
    public void stream(SseClient client) {
        String analysisId = client.ctx().pathParam("id");
        client.keepAlive();
        try {
            AutoCloseable subscription = service.subscribe(analysisId, event -> send(client, event));
            client.onClose(() -> closeQuietly(subscription));
        } catch (IllegalArgumentException e) {
            client.sendEvent("error", new SseError(e.getMessage()));
            client.close();
        }
    }
    private static void send(SseClient client, AnalysisEvent event) {
        if (event instanceof AnalysisEvent.CriterionStarted e) client.sendEvent("criterion-started", new SseCriterionStarted(e.criterionId(), e.criterionName()));
        else if (event instanceof AnalysisEvent.CriterionCompleted e) {
            var r = e.result();
            client.sendEvent("criterion-completed", new SseCriterionCompleted(r.criterion().id(), r.criterion().name(), r.successful(), r.score(), r.comment()));
        } else if (event instanceof AnalysisEvent.AnalysisCompleted e) client.sendEvent("analysis-completed", new SseAnalysisCompleted(e.analysisId(), e.overallScore()));
        else if (event instanceof AnalysisEvent.Error e) client.sendEvent("error", new SseError(e.message()));
    }
    private static void closeQuietly(AutoCloseable c) { try { c.close(); } catch (Exception ignored) { } }
}
