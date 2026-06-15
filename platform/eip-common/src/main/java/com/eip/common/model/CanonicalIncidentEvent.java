package com.eip.common.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CanonicalIncidentEvent(
        UUID incidentId,
        String serviceName,
        String severity,
        List<String> symptoms,
        Instant timestamp,
        String source,
        Map<String, Object> rawPayload,
        List<CanonicalEvent> events,
        Map<String, Integer> exceptionCounts,
        Set<String> exceptionTypes,
        Map<String, Double> errorDistribution,
        int totalEvents,
        int totalExceptions
) {
}
