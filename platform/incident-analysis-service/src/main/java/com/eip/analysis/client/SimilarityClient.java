package com.eip.analysis.client;

import com.eip.common.dto.SimilaritySearchRequest;
import com.eip.common.dto.SimilaritySearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SimilarityClient {

    private static final Logger log = LoggerFactory.getLogger(SimilarityClient.class);
    private final WebClient webClient;

    public SimilarityClient(@Value("${services.similarity.url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public SimilaritySearchResult searchSimilar(SimilaritySearchRequest request) {
        try {
            return webClient.post()
                    .uri("/api/similarity/search")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SimilaritySearchResult.class)
                    .block();
        } catch (Exception e) {
            log.warn("Similarity search failed, continuing without history: {}", e.getMessage());
            return null;
        }
    }
}
