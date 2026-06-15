package com.eip.common.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ScoredMatch(
        UUID incidentId,
        String service,
        Instant analysisDate,
        double similarityScore,
        String rootCause,
        Map<String, String> matchReasons
) {
}
