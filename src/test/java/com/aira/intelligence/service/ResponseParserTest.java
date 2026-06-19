package com.aira.intelligence.service;

import com.aira.common.dto.RcaResult;
import com.aira.common.dto.RetrievedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseParserTest {

    private ResponseParser responseParser;
    private UUID incidentId;
    private List<RetrievedContext> emptyContext;

    @BeforeEach
    void setUp() {
        responseParser = new ResponseParser();
        incidentId = UUID.randomUUID();
        emptyContext = List.of();
    }

    @Test
    void parseRcaResponse_handlesValidJson() {
        String json = """
                {
                  "rootCause": "Database connection pool exhausted",
                  "confidence": 85,
                  "severity": "HIGH",
                  "businessImpact": ["Payment delays", "Customer complaints"],
                  "recommendations": {
                    "immediate": ["Increase pool size"],
                    "shortTerm": ["Add connection monitoring"],
                    "longTerm": ["Migrate to connection-less architecture"]
                  },
                  "summary": "DB pool exhaustion causing timeouts"
                }
                """;

        RcaResult result = responseParser.parseRcaResponse(json, incidentId, emptyContext, 1000);

        assertThat(result.incidentId()).isEqualTo(incidentId);
        assertThat(result.rootCause()).isEqualTo("Database connection pool exhausted");
        assertThat(result.confidencePercent()).isEqualTo(85);
        assertThat(result.severity()).isEqualTo("HIGH");
        assertThat(result.businessImpact()).contains("Payment delays");
        assertThat(result.recommendations().get("immediate")).contains("Increase pool size");
    }

    @Test
    void parseRcaResponse_handlesJsonEmbeddedInText() {
        String response = "Here is my analysis:\n" +
                "{\"rootCause\": \"Memory leak\", \"confidence\": 70, \"severity\": \"MEDIUM\", " +
                "\"businessImpact\": [], \"recommendations\": {\"immediate\": [], \"shortTerm\": [], \"longTerm\": []}, " +
                "\"summary\": \"Memory issue\"}\n" +
                "Let me know if you need more.";

        RcaResult result = responseParser.parseRcaResponse(response, incidentId, emptyContext, 500);

        assertThat(result.rootCause()).isEqualTo("Memory leak");
        assertThat(result.confidencePercent()).isEqualTo(70);
    }

    @Test
    void parseRcaResponse_fallsBackToFreeText() {
        String freeText = "The root cause is a network timeout between services. " +
                "Recommendation: add circuit breaker pattern.";

        RcaResult result = responseParser.parseRcaResponse(freeText, incidentId, emptyContext, 300);

        assertThat(result.incidentId()).isEqualTo(incidentId);
        assertThat(result.rootCause()).contains("network timeout");
        assertThat(result.confidencePercent()).isEqualTo(30); // low confidence for free text
    }

    @Test
    void parseRcaResponse_handlesNullResponse() {
        RcaResult result = responseParser.parseRcaResponse(null, incidentId, emptyContext, 100);

        assertThat(result.incidentId()).isEqualTo(incidentId);
        assertThat(result.rootCause()).isNotEmpty();
    }
}
