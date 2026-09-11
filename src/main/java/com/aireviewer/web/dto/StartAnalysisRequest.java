package com.aireviewer.web.dto;
import java.util.List;
public record StartAnalysisRequest(String projectId, List<String> criterionIds) {
    public StartAnalysisRequest { criterionIds = criterionIds == null ? List.of() : List.copyOf(criterionIds); }
}
