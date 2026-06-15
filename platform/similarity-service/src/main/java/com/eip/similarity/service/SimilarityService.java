package com.eip.similarity.service;

import com.eip.common.dto.SimilaritySearchRequest;
import com.eip.common.dto.SimilaritySearchResult;
import com.eip.common.model.ScoredMatch;
import com.eip.similarity.client.KnowledgeStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimilarityService {

    private static final Logger log = LoggerFactory.getLogger(SimilarityService.class);
    private static final double THRESHOLD = 0.3;

    private static final double WEIGHT_SERVICE = 0.30;
    private static final double WEIGHT_EXCEPTION_TYPE = 0.35;
    private static final double WEIGHT_EVENT_CATEGORY = 0.15;
    private static final double WEIGHT_DISTRIBUTION = 0.20;

    private final KnowledgeStoreClient knowledgeStoreClient;

    public SimilarityService(KnowledgeStoreClient knowledgeStoreClient) {
        this.knowledgeStoreClient = knowledgeStoreClient;
    }

    public SimilaritySearchResult searchSimilar(SimilaritySearchRequest request) {
        log.info("Searching similar incidents for service={}, incidentId={}", request.service(), request.incidentId());

        List<Map<String, Object>> candidates = knowledgeStoreClient.findCandidates(
                request.service(), request.exceptionTypes(), request.incidentId());

        List<ScoredMatch> matches = candidates.stream()
                .map(candidate -> scoreCandidate(candidate, request))
                .filter(match -> match.similarityScore() >= THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredMatch::similarityScore).reversed())
                .limit(request.maxResults() > 0 ? request.maxResults() : 5)
                .collect(Collectors.toList());

        log.info("Found {} similar incidents out of {} candidates", matches.size(), candidates.size());

        return new SimilaritySearchResult(
                request.incidentId(),
                matches,
                candidates.size(),
                Instant.now()
        );
    }

    private ScoredMatch scoreCandidate(Map<String, Object> candidate, SimilaritySearchRequest request) {
        String candidateService = getStringField(candidate, "serviceName");
        Set<String> candidateExceptionTypes = getSetField(candidate, "exceptionTypes");
        Map<String, Double> candidateDistribution = getDistributionField(candidate, "errorDistribution");
        Set<String> candidateEventCategories = getSetField(candidate, "eventCategories");

        Set<String> requestEventCategories = request.exceptionTypes() != null ? request.exceptionTypes() : Collections.emptySet();
        Map<String, Double> requestDistribution = request.errorDistribution() != null ? request.errorDistribution() : Collections.emptyMap();

        double score = calculateScore(
                candidateService, request.service(),
                candidateExceptionTypes, request.exceptionTypes(),
                candidateEventCategories, requestEventCategories,
                candidateDistribution, requestDistribution
        );

        Map<String, String> matchReasons = buildMatchReasons(
                candidateService, request.service(),
                candidateExceptionTypes, request.exceptionTypes(),
                candidateEventCategories, requestEventCategories,
                candidateDistribution, requestDistribution
        );

        UUID incidentId = UUID.fromString(getStringField(candidate, "incidentId"));
        String rootCause = getStringField(candidate, "rootCause");
        String analysisDateStr = getStringField(candidate, "analysisDate");
        Instant analysisDate = analysisDateStr != null && !analysisDateStr.isEmpty()
                ? Instant.parse(analysisDateStr) : Instant.now();

        return new ScoredMatch(incidentId, candidateService, analysisDate, score, rootCause, matchReasons);
    }

    public double calculateScore(
            String candidateService, String requestService,
            Set<String> candidateExceptionTypes, Set<String> requestExceptionTypes,
            Set<String> candidateEventCategories, Set<String> requestEventCategories,
            Map<String, Double> candidateDistribution, Map<String, Double> requestDistribution) {

        double serviceScore = (candidateService != null && candidateService.equals(requestService)) ? 1.0 : 0.0;
        double exceptionTypeScore = jaccardIndex(
                candidateExceptionTypes != null ? candidateExceptionTypes : Collections.emptySet(),
                requestExceptionTypes != null ? requestExceptionTypes : Collections.emptySet());
        double eventCategoryScore = jaccardIndex(
                candidateEventCategories != null ? candidateEventCategories : Collections.emptySet(),
                requestEventCategories != null ? requestEventCategories : Collections.emptySet());
        double distributionScore = cosineSimilarity(
                candidateDistribution != null ? candidateDistribution : Collections.emptyMap(),
                requestDistribution != null ? requestDistribution : Collections.emptyMap());

        return WEIGHT_SERVICE * serviceScore
                + WEIGHT_EXCEPTION_TYPE * exceptionTypeScore
                + WEIGHT_EVENT_CATEGORY * eventCategoryScore
                + WEIGHT_DISTRIBUTION * distributionScore;
    }

    public double jaccardIndex(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    public double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> allKeys = new HashSet<>(a.keySet());
        allKeys.addAll(b.keySet());

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (String key : allKeys) {
            double valA = a.getOrDefault(key, 0.0);
            double valB = b.getOrDefault(key, 0.0);
            dotProduct += valA * valB;
            normA += valA * valA;
            normB += valB * valB;
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) return 0.0;

        return dotProduct / denominator;
    }

    public Map<String, String> buildMatchReasons(
            String candidateService, String requestService,
            Set<String> candidateExceptionTypes, Set<String> requestExceptionTypes,
            Set<String> candidateEventCategories, Set<String> requestEventCategories,
            Map<String, Double> candidateDistribution, Map<String, Double> requestDistribution) {

        Map<String, String> reasons = new LinkedHashMap<>();

        if (candidateService != null && candidateService.equals(requestService)) {
            reasons.put("service", "Same service: " + requestService);
        }

        Set<String> commonExceptions = new HashSet<>(
                candidateExceptionTypes != null ? candidateExceptionTypes : Collections.emptySet());
        commonExceptions.retainAll(requestExceptionTypes != null ? requestExceptionTypes : Collections.emptySet());
        if (!commonExceptions.isEmpty()) {
            reasons.put("exceptionTypes", "Common exceptions: " + String.join(", ", commonExceptions));
        }

        Set<String> commonCategories = new HashSet<>(
                candidateEventCategories != null ? candidateEventCategories : Collections.emptySet());
        commonCategories.retainAll(requestEventCategories != null ? requestEventCategories : Collections.emptySet());
        if (!commonCategories.isEmpty()) {
            reasons.put("eventCategories", "Common categories: " + String.join(", ", commonCategories));
        }

        double cosSim = cosineSimilarity(
                candidateDistribution != null ? candidateDistribution : Collections.emptyMap(),
                requestDistribution != null ? requestDistribution : Collections.emptyMap());
        if (cosSim > 0.0) {
            reasons.put("distribution", String.format("Error distribution similarity: %.2f", cosSim));
        }

        return reasons;
    }

    @SuppressWarnings("unchecked")
    private String getStringField(Map<String, Object> map, String field) {
        Object val = map.get(field);
        return val != null ? val.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private Set<String> getSetField(Map<String, Object> map, String field) {
        Object val = map.get(field);
        if (val instanceof Collection) {
            return new HashSet<>((Collection<String>) val);
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> getDistributionField(Map<String, Object> map, String field) {
        Object val = map.get(field);
        if (val instanceof Map) {
            Map<String, Object> raw = (Map<String, Object>) val;
            Map<String, Double> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }
}
