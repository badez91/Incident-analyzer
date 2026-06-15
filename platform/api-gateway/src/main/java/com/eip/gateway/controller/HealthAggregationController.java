package com.eip.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

@RestController
public class HealthAggregationController {

    private static final Logger log = LoggerFactory.getLogger(HealthAggregationController.class);

    private final Map<String, String> routeMapping;
    private final WebClient webClient;

    private static final Map<String, String> SERVICE_HEALTH_ENDPOINTS = Map.of(
            "incident-ingestion-service", "http://localhost:8081/health",
            "canonical-transformer-service", "http://localhost:8082/health",
            "incident-analysis-service", "http://localhost:8083/health",
            "knowledge-store-service", "http://localhost:8084/health",
            "similarity-service", "http://localhost:8085/health",
            "failure-simulator-service", "http://localhost:8086/health"
    );

    public HealthAggregationController(
            Map<String, String> routeMapping,
            @Qualifier("defaultGatewayWebClient") WebClient webClient) {
        this.routeMapping = routeMapping;
        this.webClient = webClient;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> aggregateHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "api-gateway");
        result.put("timestamp", Instant.now().toString());

        Map<String, Object> services = new LinkedHashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, String> entry : SERVICE_HEALTH_ENDPOINTS.entrySet()) {
            String serviceName = entry.getKey();
            String healthUrl = entry.getValue();
            try {
                String response = webClient.get()
                        .uri(healthUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                services.put(serviceName, Map.of("status", "UP", "url", healthUrl));
            } catch (Exception e) {
                services.put(serviceName, Map.of("status", "DOWN", "url", healthUrl, "error", e.getMessage()));
                allHealthy = false;
            }
        }

        result.put("status", allHealthy ? "UP" : "DEGRADED");
        result.put("services", services);

        return ResponseEntity.ok(result);
    }
}
