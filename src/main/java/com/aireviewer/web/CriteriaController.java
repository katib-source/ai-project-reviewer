package com.aireviewer.web;
import com.aireviewer.application.ReviewService;
import com.aireviewer.web.dto.CriterionDto;
import io.javalin.http.Context;
public final class CriteriaController {
    private final ReviewService service;
    public CriteriaController(ReviewService service) { this.service = service; }
    public void list(Context ctx) { ctx.json(service.listCriteria().stream().map(c -> new CriterionDto(c.id(), c.name(), c.description(), c.weight())).toList()); }
}
