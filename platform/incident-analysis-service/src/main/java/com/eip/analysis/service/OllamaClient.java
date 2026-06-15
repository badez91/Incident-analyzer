package com.eip.analysis.service;

import com.eip.common.dto.AiAnalysisResponse;
import com.eip.common.model.RecommendedActions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;

    public OllamaClient(
            @Value("${ollama.url}") String baseUrl,
            @Value("${ollama.timeout-seconds}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
    }

    public AiAnalysisResponse analyze(String prompt, String model) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("format", "json");
        request.put("stream", false);

        try {
            String responseBody = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long inferenceTime = System.currentTimeMillis() - startTime;
            return parseResponse(responseBody, model, inferenceTime);
        } catch (Exception e) {
            long inferenceTime = System.currentTimeMillis() - startTime;
            log.error("Ollama request failed: {}", e.getMessage());
            return new AiAnalysisResponse(
                    "UNKNOWN", "low", 0,
                    "AI analysis failed: " + e.getMessage(),
                    List.of("Unable to determine business impact"),
                    new RecommendedActions(List.of("Retry analysis"), List.of(), List.of()),
                    "Analysis failed due to AI service error",
                    e.getMessage(), false, model, inferenceTime
            );
        }
    }

    private AiAnalysisResponse parseResponse(String responseBody, String model, long inferenceTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String rawResponse = root.has("response") ? root.get("response").asText() : responseBody;

            JsonNode parsed = objectMapper.readTree(rawResponse);

            String severity = getTextOrDefault(parsed, "severity", "MEDIUM");
            String confidence = getTextOrDefault(parsed, "confidence", "medium");
            int confidencePercent = parsed.has("confidencePercent") ? parsed.get("confidencePercent").asInt() : 50;
            String rootCause = getTextOrDefault(parsed, "rootCause", "Unable to determine root cause");
            String summary = getTextOrDefault(parsed, "summary", rawResponse);

            List<String> businessImpact = parseStringList(parsed, "businessImpact");
            RecommendedActions recommendations = parseRecommendations(parsed);

            return new AiAnalysisResponse(
                    severity, confidence, confidencePercent,
                    rootCause, businessImpact, recommendations,
                    summary, rawResponse, true, model, inferenceTime
            );
        } catch (Exception e) {
            log.warn("Failed to parse structured AI response, using raw: {}", e.getMessage());
            return new AiAnalysisResponse(
                    "MEDIUM", "low", 30,
                    responseBody,
                    List.of("Unable to parse business impact"),
                    new RecommendedActions(List.of("Review raw AI output"), List.of(), List.of()),
                    responseBody, responseBody, false, model, inferenceTime
            );
        }
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : defaultValue;
    }

    private List<String> parseStringList(JsonNode node, String field) {
        if (!node.has(field) || !node.get(field).isArray()) {
            return List.of("Not specified");
        }
        List<String> result = new ArrayList<>();
        node.get(field).forEach(item -> result.add(item.asText()));
        return result.isEmpty() ? List.of("Not specified") : result;
    }

    private RecommendedActions parseRecommendations(JsonNode node) {
        JsonNode rec = node.has("recommendations") ? node.get("recommendations") :
                       node.has("recommendedActions") ? node.get("recommendedActions") : null;

        if (rec == null) {
            return new RecommendedActions(List.of("Review incident details"), List.of(), List.of());
        }

        List<String> immediate = parseStringList(rec, "immediate");
        List<String> shortTerm = parseStringList(rec, "shortTerm");
        List<String> longTerm = parseStringList(rec, "longTerm");

        return new RecommendedActions(immediate, shortTerm, longTerm);
    }
}
