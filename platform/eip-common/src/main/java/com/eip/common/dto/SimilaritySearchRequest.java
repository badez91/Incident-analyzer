package com.eip.common.dto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SimilaritySearchRequest(
        UUID incidentId,
        String service,
        Set<String> exceptionTypes,
        Map<String, Integer> exceptionCounts,
        Map<String, Double> errorDistribution,
        int maxResults
) {
}
