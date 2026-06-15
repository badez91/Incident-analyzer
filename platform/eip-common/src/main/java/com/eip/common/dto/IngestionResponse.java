package com.eip.common.dto;

import java.time.Instant;
import java.util.UUID;

public record IngestionResponse(
        UUID incidentId,
        String status,
        Instant acceptedAt,
        String message
) {
}
