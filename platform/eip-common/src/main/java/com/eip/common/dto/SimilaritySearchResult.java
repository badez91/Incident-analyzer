package com.eip.common.dto;

import com.eip.common.model.ScoredMatch;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SimilaritySearchResult(
        UUID queryIncidentId,
        List<ScoredMatch> matches,
        int candidatesEvaluated,
        Instant searchedAt
) {
}
