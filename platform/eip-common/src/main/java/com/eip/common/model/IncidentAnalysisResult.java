package com.eip.common.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentAnalysisResult(
        UUID analysisId,
        UUID incidentId,
        String category,
        String rootCause,
        String severity,
        double confidence,
        int confidencePercent,
        String summary,
        List<String> businessImpact,
        RecommendedActions recommendations,
        List<ScoredMatch> similarIncidents,
        List<Evidence> evidence,
        String llmModel,
        String llmProvider,
        long inferenceTimeMs,
        String status,
        Instant analyzedAt
) {
}
