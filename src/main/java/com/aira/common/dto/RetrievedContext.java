package com.aira.common.dto;

import java.util.UUID;

public record RetrievedContext(
    UUID documentId,
    String sourceType,
    String referenceId,
    String snippet,
    double similarityScore
) {}
