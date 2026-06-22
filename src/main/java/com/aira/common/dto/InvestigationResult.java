package com.aira.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Investigation result — not a definitive RCA, but a structured investigation output.
 * When confidence is low, it asks for additional information rather than guessing.
 */
public record InvestigationResult(
    UUID incidentId,
    String severity,
    String status,              // INVESTIGATING, NEEDS_INFO, HYPOTHESIS, CONFIRMED
    String hypothesis,          // Best guess based on available evidence (not stated as fact)
    int confidencePercent,
    List<String> evidenceFound, // What data supports the hypothesis
    List<String> missingInfo,   // What additional info is needed to confirm
    List<String> questionsToAsk,// Specific questions for the engineer/team
    List<String> nextSteps,     // Investigation actions to take
    Map<String, List<String>> recommendations,
    String summary,
    List<RetrievedContext> contextUsed,
    int tokensUsed,
    long inferenceTimeMs,
    Instant analyzedAt
) {}
