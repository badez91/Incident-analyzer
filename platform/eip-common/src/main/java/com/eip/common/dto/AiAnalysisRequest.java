package com.eip.common.dto;

import java.util.Map;

public record AiAnalysisRequest(
        String prompt,
        String model,
        String provider,
        Map<String, Object> parameters
) {
}
