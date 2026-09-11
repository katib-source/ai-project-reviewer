package com.aireviewer.web.dto;
public record SseCriterionCompleted(String criterionId, String criterionName, boolean successful, double score, String comment) { }
