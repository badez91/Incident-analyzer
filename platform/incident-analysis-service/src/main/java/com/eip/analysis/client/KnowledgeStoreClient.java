package com.eip.analysis.client;

import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.IncidentAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Component
public class KnowledgeStoreClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStoreClient.class);
    private final WebClient webClient;

    public KnowledgeStoreClient(@Value("${services.knowledge-store.url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public void storeIncident(CanonicalIncidentEvent incident) {
        try {
            webClient.post()
                    .uri("/api/knowledge/incidents")
                    .bodyValue(incident)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Stored incident {} in knowledge store", incident.incidentId());
        } catch (Exception e) {
            log.warn("Failed to store incident in knowledge store: {}", e.getMessage());
        }
    }

    public void storeAnalysis(IncidentAnalysisResult analysis) {
        try {
            webClient.post()
                    .uri("/api/knowledge/analyses")
                    .bodyValue(analysis)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Stored analysis {} in knowledge store", analysis.analysisId());
        } catch (Exception e) {
            log.warn("Failed to store analysis in knowledge store: {}", e.getMessage());
        }
    }

    public IncidentAnalysisResult getAnalysis(UUID incidentId) {
        try {
            return webClient.get()
                    .uri("/api/knowledge/analyses/{incidentId}", incidentId)
                    .retrieve()
                    .bodyToMono(IncidentAnalysisResult.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to get analysis for incident {}: {}", incidentId, e.getMessage());
            return null;
        }
    }
}
