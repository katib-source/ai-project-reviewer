package com.aireviewer.web.dto;
import java.util.List;
public record AnalysisResultResponse(String analysisId, double overallScore, List<CriterionResultDto> criteria) {
    public AnalysisResultResponse { criteria = List.copyOf(criteria); }
}
