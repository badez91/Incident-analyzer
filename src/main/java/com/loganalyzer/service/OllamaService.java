package com.loganalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.model.AiAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Ollama REST API client for AI-powered log analysis.
 * Forces JSON output from the LLM for structured rendering.
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private static final String PROMPT_TEMPLATE = """
            You are a senior Java production support engineer analyzing a production incident.
            
            Below is a structured JSON object containing exception data extracted from application logs.
            Each exception includes its frequency, percentage of total errors, sample error messages, and time range.
            
            Analyze this data and respond ONLY with a JSON object in this exact format:
            {
              "severity": "CRITICAL or HIGH or MEDIUM or LOW",
              "confidence": "High or Medium or Low",
              "confidencePercent": 85,
              "rootCause": "One paragraph explaining the root cause",
              "businessImpact": ["Impact point 1", "Impact point 2", "Impact point 3"],
              "recommendedActions": {
                "immediate": ["Action 1", "Action 2"],
                "shortTerm": ["Action 1", "Action 2"],
                "longTerm": ["Action 1", "Action 2"]
              },
              "summary": "One sentence summary of the incident"
            }
            
            Focus on the highest-frequency exceptions first. Look for causal chains.
            Respond ONLY with valid JSON. No markdown, no explanation outside the JSON.
            
            Input data:
            ```json
            %s
            ```""";

    private final WebClient webClient;
    private final String model;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper;

    public OllamaService(
            @Value("${ollama.url}") String ollamaUrl,
            @Value("${ollama.model}") String model,
            @Value("${ollama.timeout-seconds}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaUrl)
                .build();
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
    }

    /**
     * Send the log summary to Ollama and return a parsed AiAnalysis object.
     * Falls back to raw text display if JSON parsing fails.
     */
    public AiAnalysis getStructuredAnalysis(String summary, String modelOverride) {
        String prompt = String.format(PROMPT_TEMPLATE, summary);
        String useModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : model;

        try {
            // Request body with format: "json" to force JSON output
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", useModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("format", "json");

            Map response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null && response.containsKey("response")) {
                String jsonResponse = (String) response.get("response");
                return parseAnalysis(jsonResponse);
            }
            return AiAnalysis.error("No response received from Ollama.");

        } catch (WebClientRequestException e) {
            log.error("Failed to connect to Ollama", e);
            return AiAnalysis.error("Could not connect to Ollama. Make sure Ollama is running on localhost:11434.");
        } catch (WebClientResponseException e) {
            log.error("Ollama returned error status", e);
            return AiAnalysis.error("Ollama returned status " + e.getStatusCode().value() + ".");
        } catch (Exception e) {
            log.error("Error communicating with Ollama", e);
            return AiAnalysis.error("Error communicating with Ollama: " + e.getMessage());
        }
    }

    /**
     * Parse the JSON response from Ollama into an AiAnalysis object.
     */
    private AiAnalysis parseAnalysis(String jsonResponse) {
        try {
            return objectMapper.readValue(jsonResponse, AiAnalysis.class);
        } catch (Exception e) {
            log.warn("Failed to parse Ollama JSON response, falling back to raw text", e);
            // Fallback: put raw text into the summary field
            AiAnalysis fallback = new AiAnalysis();
            fallback.setRawResponse(jsonResponse);
            return fallback;
        }
    }
}
