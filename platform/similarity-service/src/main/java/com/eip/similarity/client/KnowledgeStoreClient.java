package com.eip.similarity.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
public class KnowledgeStoreClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStoreClient.class);
    private final WebClient webClient;

    public KnowledgeStoreClient(@Value("${services.knowledge-store.url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public List<Map<String, Object>> findCandidates(String service, Set<String> exceptionTypes, UUID excludeId) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/knowledge/candidates")
                                .queryParam("service", service);
                        if (exceptionTypes != null && !exceptionTypes.isEmpty()) {
                            uriBuilder.queryParam("exceptionTypes", String.join(",", exceptionTypes));
                        }
                        if (excludeId != null) {
                            uriBuilder.queryParam("excludeId", excludeId.toString());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch candidates from knowledge-store: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
