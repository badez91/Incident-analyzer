package com.loganalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to query Grafana Loki for log data via the Grafana datasource proxy.
 * Uses LogQL to fetch log streams and returns raw log text for analysis.
 */
@Service
public class LokiService {

    private static final Logger log = LoggerFactory.getLogger(LokiService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    private final boolean enabled;

    public LokiService(
            @Value("${grafana.url:}") String grafanaUrl,
            @Value("${grafana.service-account-token:}") String serviceAccountToken,
            @Value("${grafana.loki.datasource-uid:grafanacloud-logs}") String lokiDatasourceUid,
            @Value("${grafana.loki.timeout-seconds:60}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;

        if (grafanaUrl == null || grafanaUrl.isBlank() || serviceAccountToken == null || serviceAccountToken.isBlank()) {
            log.warn("Grafana/Loki integration disabled: grafana.url or grafana.service-account-token not configured");
            this.webClient = null;
            this.enabled = false;
        } else {
            String baseUrl = grafanaUrl.endsWith("/") ? grafanaUrl.substring(0, grafanaUrl.length() - 1) : grafanaUrl;
            String lokiProxyUrl = baseUrl + "/api/datasources/proxy/uid/" + lokiDatasourceUid;

            this.webClient = WebClient.builder()
                    .baseUrl(lokiProxyUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            this.enabled = true;
            log.info("Loki integration enabled via Grafana proxy: {}", lokiProxyUrl);
        }
    }

    /**
     * Check if Loki integration is configured and available.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Query Loki for logs matching a LogQL query within a time range.
     *
     * @param logqlQuery LogQL query (e.g., '{app="my-service"} |= "ERROR"')
     * @param start      Start of time range
     * @param end        End of time range
     * @param limit      Max number of log lines to return
     * @return Raw log text (newline-separated lines)
     */
    public LokiQueryResult queryLogs(String logqlQuery, Instant start, Instant end, int limit) {
        if (!enabled) {
            return LokiQueryResult.error("Loki integration is not configured. Set grafana.url and grafana.service-account-token in application.properties.");
        }

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/loki/api/v1/query_range")
                            .queryParam("query", logqlQuery)
                            .queryParam("start", String.valueOf(start.getEpochSecond()))
                            .queryParam("end", String.valueOf(end.getEpochSecond()))
                            .queryParam("limit", limit)
                            .queryParam("direction", "forward")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            return parseResponse(response);

        } catch (WebClientRequestException e) {
            log.error("Failed to connect to Grafana/Loki", e);
            return LokiQueryResult.error("Could not connect to Grafana. Check your URL and network: " + e.getMessage());
        } catch (WebClientResponseException e) {
            log.error("Grafana/Loki returned error status: {}", e.getStatusCode(), e);
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return LokiQueryResult.error("Authentication failed. Check your Grafana service account token.");
            }
            return LokiQueryResult.error("Grafana returned HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error querying Loki", e);
            return LokiQueryResult.error("Error querying Loki: " + e.getMessage());
        }
    }

    /**
     * Get available label values for a given label (useful for service discovery).
     */
    public List<String> getLabelValues(String labelName) {
        if (!enabled) {
            return List.of();
        }

        try {
            String response = webClient.get()
                    .uri("/loki/api/v1/label/" + labelName + "/values")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode val : data) {
                    values.add(val.asText());
                }
                return values;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch label values for '{}': {}", labelName, e.getMessage());
        }
        return List.of();
    }

    /**
     * Parse the Loki query_range response into log lines.
     */
    private LokiQueryResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.path("status").asText();

            if (!"success".equals(status)) {
                return LokiQueryResult.error("Loki query failed with status: " + status);
            }

            JsonNode result = root.path("data").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return LokiQueryResult.empty();
            }

            List<String> logLines = new ArrayList<>();
            int streamCount = 0;

            for (JsonNode stream : result) {
                streamCount++;
                JsonNode values = stream.get("values");
                if (values != null && values.isArray()) {
                    for (JsonNode entry : values) {
                        // Each entry is [timestamp_ns, log_line]
                        if (entry.isArray() && entry.size() >= 2) {
                            String logLine = entry.get(1).asText();
                            if (!logLine.isBlank()) {
                                logLines.add(logLine);
                            }
                        }
                    }
                }
            }

            if (logLines.isEmpty()) {
                return LokiQueryResult.empty();
            }

            String rawText = String.join("\n", logLines);
            return new LokiQueryResult(rawText, logLines.size(), streamCount, null);

        } catch (Exception e) {
            log.error("Failed to parse Loki response", e);
            return LokiQueryResult.error("Failed to parse Loki response: " + e.getMessage());
        }
    }

    /**
     * Result wrapper for Loki queries.
     */
    public static class LokiQueryResult {
        private final String logContent;
        private final int lineCount;
        private final int streamCount;
        private final String errorMessage;

        public LokiQueryResult(String logContent, int lineCount, int streamCount, String errorMessage) {
            this.logContent = logContent;
            this.lineCount = lineCount;
            this.streamCount = streamCount;
            this.errorMessage = errorMessage;
        }

        public static LokiQueryResult error(String message) {
            return new LokiQueryResult(null, 0, 0, message);
        }

        public static LokiQueryResult empty() {
            return new LokiQueryResult(null, 0, 0, "No log entries found matching the query and time range.");
        }

        public boolean isError() { return errorMessage != null; }
        public boolean isEmpty() { return logContent == null && errorMessage == null; }
        public boolean hasData() { return logContent != null && !logContent.isBlank(); }

        public String getLogContent() { return logContent; }
        public int getLineCount() { return lineCount; }
        public int getStreamCount() { return streamCount; }
        public String getErrorMessage() { return errorMessage; }
    }
}
