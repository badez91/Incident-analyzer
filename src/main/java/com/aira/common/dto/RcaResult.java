package com.aira.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RcaResult(
    UUID incidentId,
    String severity,
    String rootCause,
    int confidencePercent,
    List<String> businessImpact,
    Map<String, List<String>> recommendations,
    String summary,
    List<RetrievedContext> contextUsed,
    int tokensUsed,
    long inferenceTimeMs,
    Instant analyzedAt
) {}
