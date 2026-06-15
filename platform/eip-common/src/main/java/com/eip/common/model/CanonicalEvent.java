package com.eip.common.model;

import java.time.Instant;
import java.util.UUID;

public record CanonicalEvent(
        UUID eventId,
        UUID incidentId,
        Instant timestamp,
        String level,
        String service,
        String eventType,
        String message
) {
}
