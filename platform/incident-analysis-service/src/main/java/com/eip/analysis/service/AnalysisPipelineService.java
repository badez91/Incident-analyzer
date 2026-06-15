package com.eip.analysis.service;

import com.eip.analysis.client.KnowledgeStoreClient;
import com.eip.analysis.client.SimilarityClient;
import com.eip.analysis.output.OutputWriterService;
import com.eip.common.dto.AiAnalysisResponse;
import com.eip.common.dto.SimilaritySearchRequest;
import com.eip.common.dto.SimilaritySearchResult;
import com.eip.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class AnalysisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipelineService.class);
    private static final int MAX_RETRIES = 3;

    private final KnowledgeStoreClient knowledgeStoreClient;
    private final SimilarityClient similarityClient;
    private final OllamaClient ollamaClient;
    private final PromptBuilder promptBuilder;
    private final OutputWriterService outputWriterService;

    @Value("${ollama.model}")
    private String defaultModel;

    public AnalysisPipelineService(
            KnowledgeStoreClient knowledgeStoreClient,
            SimilarityClient similarityClient,
            OllamaClient ollamaClient,
            PromptBuilder promptBuilder,
            OutputWriterService outputWriterService) {
        this.knowledgeStoreClient = knowledgeStoreClient;
        this.similarityClient = similarityClient;
        this.ollamaClient = ollamaClient;
        this.promptBuilder = promptBuilder;
        this.outputWriterService = outputWriterService;
    }

    public IncidentAnalysisResult analyze(CanonicalIncidentEvent incident, String modelOverride) {
        String model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : defaultModel;
        UUID analysisId = UUID.randomUUID();

        log.info("Starting analysis pipeline for incident {} with model {}", incident.incidentId(), model);

        // Step 1: Store incident in knowledge store
        knowledgeStoreClient.storeIncident(incident);

        // Step 2: Find similar incidents
        List<ScoredMatch> similarMatches = findSimilar(incident);

        // Step 3: Build enhanced prompt
        String prompt = promptBuilder.buildPrompt(incident, similarMatches);

        // Step 4: Call AI with retry
        AiAnalysisResponse aiResponse = callAiWithRetry(prompt, model);

        // Step 5: Build result
        IncidentAnalysisResult result;
        if (aiResponse == null) {
            result = buildFailedResult(analysisId, incident, model, similarMatches);
        } else {
            result = buildSuccessResult(analysisId, incident, aiResponse, model, similarMatches);
        }

        // Step 6: Store analysis
        knowledgeStoreClient.storeAnalysis(result);

        // Step 7: Generate local output files
        try {
            outputWriterService.generateOutputs(incident, result);
        } catch (Exception e) {
            log.warn("Output generation failed (non-fatal): {}", e.getMessage());
        }

        log.info("Analysis pipeline completed for incident {} with status {}", incident.incidentId(), result.status());
        return result;
    }

    private List<ScoredMatch> findSimilar(CanonicalIncidentEvent incident) {
        try {
            SimilaritySearchRequest searchRequest = new SimilaritySearchRequest(
                    incident.incidentId(),
                    incident.serviceName(),
                    incident.exceptionTypes(),
                    incident.exceptionCounts(),
                    incident.errorDistribution(),
                    5
            );
            SimilaritySearchResult searchResult = similarityClient.searchSimilar(searchRequest);
            if (searchResult != null && searchResult.matches() != null) {
                return searchResult.matches();
            }
        } catch (Exception e) {
            log.warn("Similarity search failed, continuing without history: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private AiAnalysisResponse callAiWithRetry(String prompt, String model) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                AiAnalysisResponse response = ollamaClient.analyze(prompt, model);
                if (response != null) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("AI call attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
                log.info("Retrying AI call in {}ms", backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("All {} AI retry attempts exhausted", MAX_RETRIES);
        return null;
    }

    private IncidentAnalysisResult buildSuccessResult(
            UUID analysisId, CanonicalIncidentEvent incident,
            AiAnalysisResponse aiResponse, String model, List<ScoredMatch> similar) {

        List<Evidence> evidence = new ArrayList<>();
        if (incident.exceptionTypes() != null) {
            for (String ex : incident.exceptionTypes()) {
                evidence.add(new Evidence("exception", ex, 0.9));
            }
        }

        return new IncidentAnalysisResult(
                analysisId,
                incident.incidentId(),
                determineCategory(aiResponse.rootCause()),
                aiResponse.rootCause(),
                aiResponse.severity(),
                aiResponse.confidencePercent() / 100.0,
                aiResponse.confidencePercent(),
                aiResponse.summary(),
                aiResponse.businessImpact(),
                aiResponse.recommendedActions(),
                similar,
                evidence,
                model,
                "ollama",
                aiResponse.inferenceTimeMs(),
                "COMPLETED",
                Instant.now()
        );
    }

    private IncidentAnalysisResult buildFailedResult(
            UUID analysisId, CanonicalIncidentEvent incident, String model, List<ScoredMatch> similar) {

        return new IncidentAnalysisResult(
                analysisId,
                incident.incidentId(),
                "UNKNOWN",
                "Analysis failed - AI service unavailable",
                "UNKNOWN",
                0.0,
                0,
                "Analysis failed after all retry attempts",
                List.of("Unable to determine business impact"),
                new RecommendedActions(List.of("Retry analysis", "Check AI service"), List.of(), List.of()),
                similar,
                List.of(),
                model,
                "ollama",
                0,
                "FAILED",
                Instant.now()
        );
    }

    private String determineCategory(String rootCause) {
        if (rootCause == null) return "UNKNOWN";
        String lower = rootCause.toLowerCase();
        if (lower.contains("database") || lower.contains("connection pool") || lower.contains("sql")) return "DATABASE";
        if (lower.contains("timeout") || lower.contains("latency")) return "PERFORMANCE";
        if (lower.contains("memory") || lower.contains("heap") || lower.contains("oom")) return "RESOURCE";
        if (lower.contains("null") || lower.contains("npe")) return "CODE_BUG";
        if (lower.contains("auth") || lower.contains("permission") || lower.contains("token")) return "SECURITY";
        if (lower.contains("network") || lower.contains("dns") || lower.contains("socket")) return "NETWORK";
        return "INFRASTRUCTURE";
    }
}
