package com.aireviewer.application;

import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;

/** Application-level event model used to bridge analysis progress to transports such as SSE. */
public sealed interface AnalysisEvent
        permits AnalysisEvent.CriterionStarted, AnalysisEvent.CriterionCompleted,
                AnalysisEvent.AnalysisCompleted, AnalysisEvent.Error {

    String analysisId();

    record CriterionStarted(String analysisId, String criterionId, String criterionName)
            implements AnalysisEvent { }

    record CriterionCompleted(String analysisId, CriterionResult result)
            implements AnalysisEvent { }

    record AnalysisCompleted(String analysisId, double overallScore)
            implements AnalysisEvent { }

    record Error(String analysisId, String message)
            implements AnalysisEvent { }
}
