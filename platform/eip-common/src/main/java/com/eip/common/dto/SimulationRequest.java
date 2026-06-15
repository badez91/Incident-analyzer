package com.eip.common.dto;

import java.util.Map;

public record SimulationRequest(
        String scenario,
        String targetService,
        int logLineCount,
        Map<String, String> overrides
) {
}
