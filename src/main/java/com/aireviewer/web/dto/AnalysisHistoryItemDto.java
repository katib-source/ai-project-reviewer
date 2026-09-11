package com.aireviewer.web.dto;
import java.time.Instant;
public record AnalysisHistoryItemDto(String analysisId, String projectPath, String projectName, double overallScore, Instant completedAt) { }
