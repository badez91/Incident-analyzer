package com.eip.common.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant publishedAt,
        String sourceService,
        Map<String, Object> payload
) {
}
