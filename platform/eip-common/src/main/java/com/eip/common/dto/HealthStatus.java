package com.eip.common.dto;

import java.time.Instant;
import java.util.Map;

public record HealthStatus(
        String service,
        String status,
        Instant checkedAt,
        Map<String, String> details
) {
}
