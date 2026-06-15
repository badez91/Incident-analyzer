package com.eip.ingestion.client;

import com.eip.common.dto.IncidentSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class TransformerClient {

    private static final Logger log = LoggerFactory.getLogger(TransformerClient.class);
    private final WebClient webClient;

    public TransformerClient(@Value("${services.transformer.url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> transform(IncidentSubmission submission) {
        try {
            return webClient.post()
                    .uri("/api/transform")
                    .bodyValue(submission)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to call transformer service: {}", e.getMessage());
            throw new RuntimeException("Transformer service unavailable: " + e.getMessage(), e);
        }
    }
}
