package com.eip.simulator.service;

import com.eip.common.dto.IncidentSubmission;
import com.eip.common.dto.IngestionResponse;
import com.eip.common.dto.SimulationRequest;
import com.eip.common.dto.SimulationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
    private final ScenarioGenerator scenarioGenerator;
    private final WebClient ingestionClient;

    public SimulatorService(
            ScenarioGenerator scenarioGenerator,
            @Value("${services.ingestion.url}") String ingestionUrl) {
        this.scenarioGenerator = scenarioGenerator;
        this.ingestionClient = WebClient.builder().baseUrl(ingestionUrl).build();
    }

    public SimulationResult simulate(SimulationRequest request) {
        String scenario = request.scenario();
        String targetService = request.targetService() != null ? request.targetService() : "test-service";
        int lineCount = request.logLineCount() > 0 ? request.logLineCount() : 20;

        log.info("Simulating scenario {} for service {} with {} log lines", scenario, targetService, lineCount);

        // Generate realistic logs
        List<String> logLines = scenarioGenerator.generateLogs(scenario, targetService, lineCount);
        String logContent = String.join("\n", logLines);

        // Build description from scenario
        String description = buildDescription(scenario, targetService);

        // Submit to ingestion service
        UUID incidentId = submitToIngestion(targetService, description, logLines);

        return new SimulationResult(
                UUID.randomUUID(),
                incidentId,
                scenario,
                targetService,
                logLines.size(),
                Instant.now(),
                "SIMULATED"
        );
    }

    private UUID submitToIngestion(String targetService, String description, List<String> logLines) {
        try {
            IncidentSubmission submission = new IncidentSubmission(
                    targetService,
                    description,
                    logLines.size() > 20 ? logLines.subList(0, 20) : logLines,
                    Map.of("source", "failure-simulator", "generated", "true"),
                    "simulator",
                    null
            );

            IngestionResponse response = ingestionClient.post()
                    .uri("/api/incidents")
                    .bodyValue(submission)
                    .retrieve()
                    .bodyToMono(IngestionResponse.class)
                    .block();

            if (response != null && response.incidentId() != null) {
                log.info("Submitted simulation to ingestion service, incidentId={}", response.incidentId());
                return response.incidentId();
            }
        } catch (Exception e) {
            log.warn("Failed to submit to ingestion service: {}", e.getMessage());
        }
        return UUID.randomUUID();
    }

    private String buildDescription(String scenario, String targetService) {
        return switch (scenario.toUpperCase()) {
            case "DATABASE_UNAVAILABLE" -> String.format(
                    "Database connection failure in %s. Multiple connection pool exhaustion errors and JDBC connection failures detected.", targetService);
            case "SERVICE_TIMEOUT" -> String.format(
                    "Service timeout in %s. Downstream service calls exceeding timeout thresholds with cascading failures.", targetService);
            case "NULL_POINTER_EXCEPTION" -> String.format(
                    "NullPointerException in %s. Unexpected null reference encountered during request processing.", targetService);
            case "MEMORY_EXHAUSTION" -> String.format(
                    "Memory exhaustion in %s. JVM heap usage critical with OutOfMemoryError occurrences.", targetService);
            case "CONNECTION_REFUSED" -> String.format(
                    "Connection refused in %s. Unable to establish connection to downstream service.", targetService);
            case "LATENCY_SPIKE" -> String.format(
                    "Latency spike in %s. Request processing times exceeding SLA thresholds.", targetService);
            case "THREAD_DEADLOCK" -> String.format(
                    "Thread deadlock in %s. Thread pool exhaustion with rejected execution exceptions.", targetService);
            case "AUTHENTICATION_FAILURE" -> String.format(
                    "Authentication failure in %s. Multiple JWT validation errors and access denied exceptions.", targetService);
            case "DISK_FULL" -> String.format(
                    "Disk full in %s. I/O operations failing due to no space left on device.", targetService);
            case "NETWORK_PARTITION" -> String.format(
                    "Network partition in %s. DNS resolution failures and unreachable hosts detected.", targetService);
            default -> String.format("Simulated failure scenario '%s' in %s.", scenario, targetService);
        };
    }
}
