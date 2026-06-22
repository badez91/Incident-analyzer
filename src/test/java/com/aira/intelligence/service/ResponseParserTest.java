package com.aira.intelligence.service;

import com.aira.common.dto.InvestigationResult;
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
    void parseInvestigationResponse_handlesValidJson() {
        String json = """
                {
                  "hypothesis": "Database connection pool exhausted due to leak in batch processor",
                  "confidence": 85,
                  "severity": "HIGH",
                  "evidenceFound": ["Timeout errors in logs", "Pool metrics at 100%"],
                  "missingInfo": ["Deployment history"],
                  "questionsToAsk": ["Was there a recent deployment?"],
                  "nextSteps": ["Check connection pool metrics"],
                  "recommendations": {
                    "immediate": ["Increase pool size"],
                    "shortTerm": ["Add connection monitoring"],
                    "longTerm": ["Fix the connection leak"]
                  },
                  "summary": "Likely connection pool exhaustion — need deployment history to confirm"
                }
                """;

        InvestigationResult result = responseParser.parseInvestigationResponse(json, incidentId, emptyContext, 1000);

        assertThat(result.incidentId()).isEqualTo(incidentId);
        assertThat(result.hypothesis()).contains("Database connection pool exhausted");
        assertThat(result.confidencePercent()).isEqualTo(85);
        assertThat(result.status()).isEqualTo("HYPOTHESIS"); // 85% but has missingInfo
        assertThat(result.evidenceFound()).contains("Timeout errors in logs");
        assertThat(result.missingInfo()).contains("Deployment history");
        assertThat(result.questionsToAsk()).contains("Was there a recent deployment?");
    }

    @Test
    void parseInvestigationResponse_handlesJsonEmbeddedInText() {
        String response = "Here is my analysis:\n" +
                "{\"hypothesis\": \"Memory leak in service\", \"confidence\": 70, \"severity\": \"MEDIUM\", " +
                "\"evidenceFound\": [\"OOM errors\"], \"missingInfo\": [\"heap dump\"], " +
                "\"questionsToAsk\": [], \"nextSteps\": [\"collect heap dump\"], " +
                "\"recommendations\": {\"immediate\": [], \"shortTerm\": [], \"longTerm\": []}, " +
                "\"summary\": \"Likely memory leak\"}\n";

        InvestigationResult result = responseParser.parseInvestigationResponse(response, incidentId, emptyContext, 500);

        assertThat(result.hypothesis()).isEqualTo("Memory leak in service");
        assertThat(result.confidencePercent()).isEqualTo(70);
        assertThat(result.status()).isEqualTo("HYPOTHESIS");
    }

    @Test
    void parseInvestigationResponse_fallsBackToNeedsInfo() {
        String freeText = "I need more information to investigate this properly.";

        InvestigationResult result = responseParser.parseInvestigationResponse(freeText, incidentId, emptyContext, 300);

        assertThat(result.incidentId()).isEqualTo(incidentId);
        assertThat(result.status()).isEqualTo("NEEDS_INFO");
        assertThat(result.confidencePercent()).isEqualTo(20);
        assertThat(result.missingInfo()).isNotEmpty();
        assertThat(result.questionsToAsk()).isNotEmpty();
    }

    @Test
    void parseInvestigationResponse_handlesNull() {
        InvestigationResult result = responseParser.parseInvestigationResponse(null, incidentId, emptyContext, 100);

        assertThat(result.incidentId()).isEqualTo(incidentId);
        assertThat(result.status()).isEqualTo("NEEDS_INFO");
        assertThat(result.confidencePercent()).isEqualTo(0);
        assertThat(result.missingInfo()).isNotEmpty();
        assertThat(result.questionsToAsk()).isNotEmpty();
    }

    @Test
    void parseRcaResponse_backwardCompatible() {
        String json = """
                {"hypothesis": "Test cause", "confidence": 60, "severity": "LOW",
                 "evidenceFound": [], "missingInfo": [], "questionsToAsk": [], "nextSteps": [],
                 "recommendations": {"immediate": [], "shortTerm": [], "longTerm": []},
                 "summary": "test"}
                """;

        RcaResult result = responseParser.parseRcaResponse(json, incidentId, emptyContext, 500);
        assertThat(result.rootCause()).isEqualTo("Test cause");
        assertThat(result.confidencePercent()).isEqualTo(60);
    }
}
