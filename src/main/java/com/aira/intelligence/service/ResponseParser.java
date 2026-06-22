package com.aira.intelligence.service;

import com.aira.common.dto.InvestigationResult;
import com.aira.common.dto.RcaResult;
import com.aira.common.dto.RetrievedContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse LLM response into InvestigationResult.
     */
    public InvestigationResult parseInvestigationResponse(String rawResponse, UUID incidentId,
                                                           List<RetrievedContext> context, long inferenceMs) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return buildNeedsInfoResult(incidentId, context, inferenceMs);
        }

        try {
            String jsonStr = extractJsonBlock(rawResponse);
            JsonNode root = objectMapper.readTree(jsonStr);

            String hypothesis = getTextOrDefault(root, "hypothesis", null);
            int confidence = root.has("confidence") ? root.get("confidence").asInt(30) : 30;
            String severity = getTextOrDefault(root, "severity", "MEDIUM");
            List<String> evidenceFound = parseStringArray(root, "evidenceFound");
            List<String> missingInfo = parseStringArray(root, "missingInfo");
            List<String> questionsToAsk = parseStringArray(root, "questionsToAsk");
            List<String> nextSteps = parseStringArray(root, "nextSteps");
            Map<String, List<String>> recommendations = parseRecommendations(root);
            String summary = getTextOrDefault(root, "summary", hypothesis);

            // Determine status based on confidence and available info
            String status;
            if (confidence >= 80 && missingInfo.isEmpty()) {
                status = "CONFIRMED";
            } else if (confidence >= 50) {
                status = "HYPOTHESIS";
            } else {
                status = "NEEDS_INFO";
            }

            // If no hypothesis found, try rootCause field (backward compat)
            if (hypothesis == null || hypothesis.isBlank()) {
                hypothesis = getTextOrDefault(root, "rootCause", "Insufficient evidence to form hypothesis");
            }

            int tokensUsed = estimateTokens(rawResponse);

            return new InvestigationResult(
                    incidentId, severity, status, hypothesis, confidence,
                    evidenceFound, missingInfo, questionsToAsk, nextSteps,
                    recommendations, summary, context, tokensUsed, inferenceMs, Instant.now()
            );
        } catch (Exception e) {
            log.debug("JSON parse failed, using free-text fallback: {}", e.getMessage());
        }

        return parseFreeTextInvestigation(rawResponse, incidentId, context, inferenceMs);
    }

    /**
     * Backward-compatible: parse into RcaResult (used by existing tests).
     */
    public RcaResult parseRcaResponse(String rawResponse, UUID incidentId,
                                       List<RetrievedContext> context, long inferenceMs) {
        InvestigationResult inv = parseInvestigationResponse(rawResponse, incidentId, context, inferenceMs);
        return new RcaResult(
                inv.incidentId(), inv.severity(), inv.hypothesis(), inv.confidencePercent(),
                inv.evidenceFound(), inv.recommendations(), inv.summary(),
                inv.contextUsed(), inv.tokensUsed(), inv.inferenceTimeMs(), inv.analyzedAt()
        );
    }

    private InvestigationResult parseFreeTextInvestigation(String rawResponse, UUID incidentId,
                                                            List<RetrievedContext> context, long inferenceMs) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return buildNeedsInfoResult(incidentId, context, inferenceMs);
        }

        String hypothesis = extractKeyPhrase(rawResponse, "hypothesis");
        if (hypothesis == null) hypothesis = extractKeyPhrase(rawResponse, "root cause");
        if (hypothesis == null) hypothesis = rawResponse.substring(0, Math.min(rawResponse.length(), 300));

        int tokensUsed = estimateTokens(rawResponse);

        return new InvestigationResult(
                incidentId, "MEDIUM", "NEEDS_INFO", hypothesis, 20,
                List.of("Limited — extracted from unstructured response"),
                List.of("Server logs with timestamps around incident time",
                        "Deployment history for the affected service",
                        "Stack trace or error output from the application"),
                List.of("Was there a deployment or config change before this incident?",
                        "Can you provide the full error log/stack trace?",
                        "Is this issue still occurring or has it self-recovered?"),
                List.of("Collect server logs from the affected timeframe",
                        "Check deployment history for recent changes",
                        "Verify if the issue is reproducible"),
                Map.of("immediate", List.of("Investigate further before taking action"),
                        "shortTerm", List.of(), "longTerm", List.of()),
                "Investigation incomplete — additional information needed",
                context, tokensUsed, inferenceMs, Instant.now()
        );
    }

    private InvestigationResult buildNeedsInfoResult(UUID incidentId, List<RetrievedContext> context, long inferenceMs) {
        return new InvestigationResult(
                incidentId, "MEDIUM", "NEEDS_INFO",
                "Cannot form hypothesis — insufficient evidence provided",
                0,
                List.of(),
                List.of("Full error message or stack trace",
                        "Server logs from the time of incident",
                        "Recent deployment or configuration changes",
                        "Steps to reproduce the issue"),
                List.of("What exactly was the user doing when this occurred?",
                        "When did this start happening?",
                        "Was there any deployment or change before it started?",
                        "Is this affecting all users or specific ones?"),
                List.of("Collect error logs from the application",
                        "Check recent deployment history",
                        "Attempt to reproduce in staging environment"),
                Map.of("immediate", List.of("Gather more information before taking action"),
                        "shortTerm", List.of(), "longTerm", List.of()),
                "Cannot investigate without more data",
                context, 0, inferenceMs, Instant.now()
        );
    }

    private String extractJsonBlock(String text) {
        if (text == null) throw new RuntimeException("null response");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new RuntimeException("No JSON block found");
    }

    private String getTextOrDefault(JsonNode root, String field, String defaultVal) {
        return root.has(field) && !root.get(field).isNull()
                ? root.get(field).asText()
                : defaultVal;
    }

    private List<String> parseStringArray(JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        if (root.has(field) && root.get(field).isArray()) {
            for (JsonNode item : root.get(field)) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private Map<String, List<String>> parseRecommendations(JsonNode root) {
        Map<String, List<String>> recs = new HashMap<>();
        recs.put("immediate", List.of());
        recs.put("shortTerm", List.of());
        recs.put("longTerm", List.of());

        if (root.has("recommendations") && root.get("recommendations").isObject()) {
            JsonNode recsNode = root.get("recommendations");
            if (recsNode.has("immediate")) recs.put("immediate", parseStringArray(recsNode, "immediate"));
            if (recsNode.has("shortTerm")) recs.put("shortTerm", parseStringArray(recsNode, "shortTerm"));
            if (recsNode.has("longTerm")) recs.put("longTerm", parseStringArray(recsNode, "longTerm"));
        }
        return recs;
    }

    private String extractKeyPhrase(String text, String keyword) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        int idx = lower.indexOf(keyword);
        if (idx < 0) return null;

        int start = idx + keyword.length();
        while (start < text.length() && (text.charAt(start) == ':' || text.charAt(start) == '-' || text.charAt(start) == ' ')) {
            start++;
        }
        int end = text.indexOf('.', start);
        if (end < 0) end = Math.min(text.length(), start + 200);
        return text.substring(start, end).trim();
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }
}
