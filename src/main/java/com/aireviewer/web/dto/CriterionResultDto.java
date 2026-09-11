package com.aireviewer.web.dto;
public record CriterionResultDto(String criterionId, String criterionName, boolean successful, double score, String comment) { }
