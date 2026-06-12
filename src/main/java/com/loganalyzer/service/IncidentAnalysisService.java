package com.loganalyzer.service;

import com.loganalyzer.model.AiAnalysis;
import com.loganalyzer.model.CanonicalEvent;
import com.loganalyzer.model.IncidentAnalysis;
import com.loganalyzer.model.ScoredMatch;
import com.loganalyzer.repository.KnowledgeStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full analysis pipeline:
 * parse exceptions → transform to canonical events → find similar incidents →
 * build enhanced prompt → call LLM → persist results.
 */
@Service
public class IncidentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAnalysisService.class);

    private static final int MAX_SIMILAR_INCIDENTS = 5;
    private static final int MAX_HISTORY_CHARS = 8000;
    private static final int MAX_RAW_RESPONSE_CHARS = 32000;

    private static final String HISTORICAL_CONTEXT_HEADER = """
            
            --- HISTORICAL CONTEXT ---
            Similar incidents found in our knowledge base:
            
            """;

    private static final String HISTORICAL_CONTEXT_FOOTER = """
            Consider these historical incidents when determining root cause. \
            Note similarities and differences from past occurrences.
            """;

    private final LogParserService logParserService;
    private final CanonicalEventTransformer canonicalEventTransformer;
    private final SimilarityMatchingService similarityMatchingService;
    private final OllamaService ollamaService;
    private final KnowledgeStoreRepository knowledgeStoreRepository;
    private final DataModelValidator validator;
    private final String defaultModel;

    public IncidentAnalysisService(
            LogParserService logParserService,
            CanonicalEventTransformer canonicalEventTransformer,
            SimilarityMatchingService similarityMatchingService,
            OllamaService ollamaService,
            KnowledgeStoreRepository knowledgeStoreRepository,
            DataModelValidator validator,
            @Value("${ollama.model}") String defaultModel) {
        this.logParserService = logParserService;
        this.canonicalEventTransformer = canonicalEventTransformer;
        this.similarityMatchingService = similarityMatchingService;
        this.ollamaService = ollamaService;
        this.knowledgeStoreRepository = knowledgeStoreRepository;
        this.validator = validator;
        this.defaultModel = defaultModel;
    }

    /**
     * Full analysis pipeline: parse, transform, match, prompt, LLM, persist.
     * Returns the completed IncidentAnalysis record.
     */
    public IncidentAnalysis analyzeAndPersist(String rawLogContent, String sourceFilename, String modelOverride) {
        // Step 1: Parse exceptions
        Map<String, Integer> exceptionCounts = logParserService.parseLog(rawLogContent);
        if (exceptionCounts.isEmpty()) {
            throw new AnalysisException("No parseable exceptions found in the log content.");
        }

        // Step 2: Transform to canonical events
        List<CanonicalEvent> events = canonicalEventTransformer.transform(rawLogContent);

        // Step 3: Build incident summary
        IncidentAnalysis incident = buildIncidentSummary(events, exceptionCounts, sourceFilename);

        // Step 4: Find similar historical incidents
        List<ScoredMatch> similarIncidents = List.of();
        try {
            similarIncidents = similarityMatchingService.findSimilar(incident, MAX_SIMILAR_INCIDENTS);
            log.info("Found {} similar incidents for analysis {}", similarIncidents.size(), incident.getIncidentId());
        } catch (Exception e) {
            log.warn("Similarity search failed, continuing without historical context: {}", e.getMessage());
        }

        // Step 5: Build enhanced prompt with historical context
        String enrichedSummary = logParserService.buildEnrichedSummary(rawLogContent);
        String enhancedPrompt = buildEnhancedPrompt(enrichedSummary, similarIncidents);

        // Step 6: Call LLM
        String modelToUse = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : defaultModel;
        AiAnalysis llmResult = null;
        try {
            llmResult = ollamaService.getStructuredAnalysis(enhancedPrompt, modelToUse);
        } catch (Exception e) {
            log.error("LLM call failed for incident {}: {}", incident.getIncidentId(), e.getMessage());
            incident.setStatus("INCOMPLETE");
            incident.setErrorMessage("LLM analysis failed: " + e.getMessage());
        }

        // Step 7: Compose final incident
        if (llmResult != null) {
            populateFromLlmAnalysis(incident, llmResult);
        }

        // Step 8: Persist to knowledge store
        persistWithRetry(incident, events);

        return incident;
    }

    /**
     * Build an IncidentAnalysis summary from canonical events and exception counts.
     */
    public IncidentAnalysis buildIncidentSummary(List<CanonicalEvent> events,
                                                  Map<String, Integer> exceptionCounts,
                                                  String sourceFilename) {
        IncidentAnalysis incident = new IncidentAnalysis();
        incident.setSourceFilename(sourceFilename);
        incident.setExceptionCounts(exceptionCounts);
        incident.setExceptionTypes(new LinkedHashSet<>(exceptionCounts.keySet()));
        incident.setUniqueExceptionTypes(exceptionCounts.size());

        // Calculate total exceptions
        int totalExceptions = exceptionCounts.values().stream().mapToInt(Integer::intValue).sum();
        incident.setTotalExceptions(totalExceptions);
        incident.setTotalEvents(events.size());

        // Derive error distribution (percentage per exception type)
        Map<String, Double> distribution = new LinkedHashMap<>();
        if (totalExceptions > 0) {
            for (Map.Entry<String, Integer> entry : exceptionCounts.entrySet()) {
                double pct = (entry.getValue() * 100.0) / totalExceptions;
                distribution.put(entry.getKey(), Math.round(pct * 10.0) / 10.0);
            }
            // Adjust last entry to ensure sum is exactly 100.0
            adjustDistribution(distribution);
        }
        incident.setErrorDistribution(distribution);

        // Detect service from events
        String service = detectPrimaryService(events);
        incident.setService(service);

        // Detect time range
        Instant earliest = null;
        Instant latest = null;
        for (CanonicalEvent event : events) {
            if (event.getTimestamp() != null) {
                if (earliest == null || event.getTimestamp().isBefore(earliest)) {
                    earliest = event.getTimestamp();
                }
                if (latest == null || event.getTimestamp().isAfter(latest)) {
                    latest = event.getTimestamp();
                }
            }
        }
        incident.setTimeRangeStart(earliest);
        incident.setTimeRangeEnd(latest);

        return incident;
    }

    /**
     * Build an enhanced LLM prompt that includes historical context from similar incidents.
     * Caps the historical context section at 8000 characters.
     * Omits incidents with null rootCause.
     */
    public String buildEnhancedPrompt(String currentSummary, List<ScoredMatch> similarIncidents) {
        if (similarIncidents == null || similarIncidents.isEmpty()) {
            return currentSummary;
        }

        // Filter out incidents with null rootCause
        List<ScoredMatch> validMatches = similarIncidents.stream()
                .filter(m -> m.getRootCause() != null && !m.getRootCause().isBlank())
                .collect(Collectors.toList());

        if (validMatches.isEmpty()) {
            return currentSummary;
        }

        StringBuilder historySection = new StringBuilder();
        historySection.append(HISTORICAL_CONTEXT_HEADER);

        int count = 0;
        for (ScoredMatch match : validMatches) {
            String entry = String.format("""
                    Incident %d (%.0f%% similar):
                      Service: %s
                      Root Cause: %s
                      Match Factors: %s
                    
                    """,
                    count + 1,
                    match.getSimilarityScore() * 100,
                    match.getService(),
                    match.getRootCause(),
                    match.getMatchReasons()
            );

            // Check if adding this entry would exceed the cap
            if (historySection.length() + entry.length() + HISTORICAL_CONTEXT_FOOTER.length() > MAX_HISTORY_CHARS) {
                break;
            }

            historySection.append(entry);
            count++;
        }

        if (count == 0) {
            return currentSummary;
        }

        historySection.append(HISTORICAL_CONTEXT_FOOTER);

        return currentSummary + historySection;
    }

    // --- Private helpers ---

    private void populateFromLlmAnalysis(IncidentAnalysis incident, AiAnalysis llmResult) {
        incident.setLlmAnalysis(llmResult);

        if (llmResult.isError()) {
            incident.setStatus("INCOMPLETE");
            incident.setErrorMessage(llmResult.getErrorMessage());
            return;
        }

        if (llmResult.isRawFallback()) {
            String raw = llmResult.getRawResponse();
            if (raw != null && raw.length() > MAX_RAW_RESPONSE_CHARS) {
                raw = raw.substring(0, MAX_RAW_RESPONSE_CHARS);
            }
            incident.setRawResponse(raw);
            incident.setStatus("INCOMPLETE");
            return;
        }

        // Structured response
        incident.setSeverity(llmResult.getSeverity());
        incident.setConfidence(llmResult.getConfidence());
        incident.setConfidencePercent(llmResult.getConfidencePercent());
        incident.setRootCause(llmResult.getRootCause());
        incident.setSummary(llmResult.getSummary());

        if (llmResult.getBusinessImpact() != null) {
            incident.setImpact(String.join("; ", llmResult.getBusinessImpact()));
        }

        if (llmResult.getRecommendedActions() != null) {
            List<String> allActions = new ArrayList<>();
            if (llmResult.getRecommendedActions().getImmediate() != null)
                allActions.addAll(llmResult.getRecommendedActions().getImmediate());
            if (llmResult.getRecommendedActions().getShortTerm() != null)
                allActions.addAll(llmResult.getRecommendedActions().getShortTerm());
            if (llmResult.getRecommendedActions().getLongTerm() != null)
                allActions.addAll(llmResult.getRecommendedActions().getLongTerm());
            incident.setRecommendations(allActions);
        }
    }

    /**
     * Persist with exponential backoff retry (1s, 2s, 4s).
     */
    private void persistWithRetry(IncidentAnalysis incident, List<CanonicalEvent> events) {
        int maxRetries = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                knowledgeStoreRepository.saveWithEvents(incident, events);
                log.info("Persisted incident {} on attempt {}", incident.getIncidentId(), attempt);
                return;
            } catch (DataModelValidator.DataValidationException e) {
                // Validation failure — do not retry
                log.error("Validation failed for incident {}: {}", incident.getIncidentId(), e.getMessage());
                throw e;
            } catch (Exception e) {
                log.warn("Persistence attempt {}/{} failed for incident {}: {}",
                        attempt, maxRetries, incident.getIncidentId(), e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delayMs *= 2;
                } else {
                    log.error("All persistence retries exhausted for incident {}", incident.getIncidentId());
                    incident.setStatus("FAILED");
                    incident.setErrorMessage("Persistence failed after " + maxRetries + " retries: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Detect the primary service from canonical events (most frequent non-UNKNOWN service).
     */
    private String detectPrimaryService(List<CanonicalEvent> events) {
        Map<String, Long> serviceCounts = events.stream()
                .map(CanonicalEvent::getService)
                .filter(s -> s != null && !"UNKNOWN".equals(s))
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        return serviceCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }

    /**
     * Adjust error distribution so percentages sum to exactly 100.0.
     */
    private void adjustDistribution(Map<String, Double> distribution) {
        if (distribution.isEmpty()) return;

        double sum = distribution.values().stream().mapToDouble(Double::doubleValue).sum();
        double diff = 100.0 - sum;

        if (Math.abs(diff) > 0.001) {
            // Add the difference to the last entry
            String lastKey = null;
            for (String key : distribution.keySet()) {
                lastKey = key;
            }
            if (lastKey != null) {
                distribution.put(lastKey, distribution.get(lastKey) + diff);
            }
        }
    }

    /**
     * Exception thrown when analysis cannot proceed.
     */
    public static class AnalysisException extends RuntimeException {
        public AnalysisException(String message) {
            super(message);
        }
    }
}
