package com.aira.intelligence.service;

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

    public RcaResult parseRcaResponse(String rawResponse, UUID incidentId,
                                       List<RetrievedContext> context, long inferenceMs) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return buildFallbackResult(incidentId, context, inferenceMs);
        }

        // Attempt JSON parse first
        try {
            String jsonStr = extractJsonBlock(rawResponse);
            JsonNode root = objectMapper.readTree(jsonStr);

            String rootCause = getTextOrDefault(root, "rootCause", rawResponse);
            int confidence = root.has("confidence") ? root.get("confidence").asInt(50) : 50;
            String severity = getTextOrDefault(root, "severity", "MEDIUM");

            List<String> businessImpact = parseStringArray(root, "businessImpact");
            Map<String, List<String>> recommendations = parseRecommendations(root);
            String summary = getTextOrDefault(root, "summary", rootCause);

            int tokensUsed = estimateTokens(rawResponse);

            return new RcaResult(
                    incidentId, severity, rootCause, confidence,
                    businessImpact, recommendations, summary,
                    context, tokensUsed, inferenceMs, Instant.now()
            );
        } catch (Exception e) {
            log.debug("JSON parse failed, attempting free-text extraction: {}", e.getMessage());
        }

        // Fallback: extract from free text
        return parseFreeText(rawResponse, incidentId, context, inferenceMs);
    }

    private RcaResult parseFreeText(String rawResponse, UUID incidentId,
                                     List<RetrievedContext> context, long inferenceMs) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return buildFallbackResult(incidentId, context, inferenceMs);
        }

        String rootCause = extractKeyPhrase(rawResponse, "root cause");
        if (rootCause == null || rootCause.isBlank()) {
            rootCause = rawResponse.substring(0, Math.min(rawResponse.length(), 500));
        }

        String recommendation = extractKeyPhrase(rawResponse, "recommendation");
        List<String> recs = recommendation != null ? List.of(recommendation) : List.of("Review logs and monitor");

        Map<String, List<String>> recommendations = new HashMap<>();
        recommendations.put("immediate", recs);
        recommendations.put("shortTerm", List.of());
        recommendations.put("longTerm", List.of());

        int tokensUsed = estimateTokens(rawResponse);

        return new RcaResult(
                incidentId, "MEDIUM", rootCause, 30,
                List.of("Requires manual review"),
                recommendations,
                "Auto-extracted from free-text response",
                context, tokensUsed, inferenceMs, Instant.now()
        );
    }

    private String extractJsonBlock(String text) {
        if (text == null) throw new RuntimeException("null response");
        // Find JSON object in response
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new RuntimeException("No JSON block found");
    }

    private RcaResult buildFallbackResult(UUID incidentId, List<RetrievedContext> context, long inferenceMs) {
        Map<String, List<String>> recommendations = new HashMap<>();
        recommendations.put("immediate", List.of("Review logs and monitor"));
        recommendations.put("shortTerm", List.of());
        recommendations.put("longTerm", List.of());

        return new RcaResult(
                incidentId, "MEDIUM", "Unable to determine root cause", 0,
                List.of("Analysis unavailable"),
                recommendations,
                "LLM response was empty or unavailable",
                context, 0, inferenceMs, Instant.now()
        );
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
        // Skip colon/dash/space after keyword
        while (start < text.length() && (text.charAt(start) == ':' || text.charAt(start) == '-' || text.charAt(start) == ' ')) {
            start++;
        }
        int end = text.indexOf('.', start);
        if (end < 0) end = Math.min(text.length(), start + 200);
        return text.substring(start, end).trim();
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        // Rough estimate: ~4 chars per token
        return text.length() / 4;
    }
}
