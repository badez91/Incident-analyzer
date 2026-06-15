package com.eip.common.dto;

import java.time.Instant;
import java.util.UUID;

public record SimulationResult(
        UUID simulationId,
        UUID incidentId,
        String scenario,
        String targetService,
        int generatedLogLines,
        Instant simulatedAt,
        String status
) {
}
