package com.aira.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final WebClient webClient;
    private final String embeddingModel;
    private final int timeoutSeconds;

    public EmbeddingService(
            @Value("${ollama.url}") String ollamaUrl,
            @Value("${ollama.embedding-model}") String embeddingModel,
            @Value("${ollama.timeout-seconds}") int timeoutSeconds) {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaUrl)
                .build();
        this.embeddingModel = embeddingModel;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Generate an embedding vector for the given text using Ollama's embedding API.
     * Returns null if Ollama is unavailable — embedding is optional, metadata search still works.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Cannot generate embedding for null/blank text");
            return null;
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "prompt", text
            );

            EmbeddingResponse response = webClient.post()
                    .uri("/api/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null && response.embedding() != null && !response.embedding().isEmpty()) {
                List<Double> embedding = response.embedding();
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = embedding.get(i).floatValue();
                }
                log.debug("Generated embedding with {} dimensions for text of length {}",
                        result.length, text.length());
                return result;
            }

            log.warn("Empty embedding response from Ollama");
            return null;

        } catch (WebClientResponseException e) {
            log.warn("Ollama embedding API error (status={}): {}",
                    e.getStatusCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Ollama unavailable for embedding generation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Response record for Ollama /api/embeddings endpoint.
     */
    private record EmbeddingResponse(List<Double> embedding) {}
}
