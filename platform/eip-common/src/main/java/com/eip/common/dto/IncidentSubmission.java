package com.eip.common.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record IncidentSubmission(
        @NotBlank String serviceName,
        @NotBlank String description,
        List<String> logSnippets,
        Map<String, String> metadata,
        String source,
        String severity
) {
}
