package com.aira.intelligence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient webClient;
    private final int timeoutSeconds;

    public OllamaClient(
            @Value("${ollama.url:http://localhost:11434}") String ollamaUrl,
            @Value("${ollama.timeout-seconds:120}") int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.webClient = WebClient.builder()
                .baseUrl(ollamaUrl)
                .build();
        log.info("Ollama client initialized: url={}, timeout={}s", ollamaUrl, timeoutSeconds);
    }

    /**
     * Sends a prompt to Ollama and returns the response text.
     * Returns Optional.empty() if the call fails or returns null — callers decide how to degrade.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> analyze(String prompt, String model) {
        try {
            log.debug("Calling Ollama: model={}, promptLength={}", model, prompt.length());

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false
            );

            Map<String, Object> response = webClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response == null) {
                log.warn("Ollama returned null response");
                return Optional.empty();
            }

            String result = (String) response.get("response");
            log.debug("Ollama response received: length={}", result != null ? result.length() : 0);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("Ollama call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
