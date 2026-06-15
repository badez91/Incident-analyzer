package com.eip.common.dto;

import com.eip.common.model.RecommendedActions;

import java.util.List;

public record AiAnalysisResponse(
        String severity,
        String confidence,
        int confidencePercent,
        String rootCause,
        List<String> businessImpact,
        RecommendedActions recommendedActions,
        String summary,
        String rawResponse,
        boolean structured,
        String model,
        long inferenceTimeMs
) {
}
