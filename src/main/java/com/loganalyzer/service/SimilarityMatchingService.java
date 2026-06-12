package com.loganalyzer.service;

import com.loganalyzer.model.IncidentAnalysis;
import com.loganalyzer.model.ScoredMatch;
import com.loganalyzer.repository.KnowledgeStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Finds historically similar incidents using deterministic matching criteria.
 * Uses weighted multi-factor scoring without vector databases.
 *
 * Weights:
 *   - Service match: 30%
 *   - Exception type overlap (Jaccard): 35%
 *   - Event category overlap: 15%
 *   - Error distribution similarity (Cosine): 20%
 */
@Service
public class SimilarityMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SimilarityMatchingService.class);

    private static final double MINIMUM_THRESHOLD = 0.3;
    private static final double SERVICE_WEIGHT = 0.30;
    private static final double EXCEPTION_TYPE_WEIGHT = 0.35;
    private static final double EVENT_CATEGORY_WEIGHT = 0.15;
    private static final double ERROR_DISTRIBUTION_WEIGHT = 0.20;

    private final KnowledgeStoreRepository knowledgeStoreRepository;

    public SimilarityMatchingService(KnowledgeStoreRepository knowledgeStoreRepository) {
        this.knowledgeStoreRepository = knowledgeStoreRepository;
    }

    /**
     * Find similar historical incidents for the given incident.
     * Returns top-N matches above the minimum threshold, sorted by score descending.
     */
    public List<ScoredMatch> findSimilar(IncidentAnalysis currentIncident, int maxResults) {
        if (currentIncident == null || currentIncident.getIncidentId() == null) {
            return List.of();
        }

        // Query candidates from DB (pre-filtered by service OR exception types)
        List<IncidentAnalysis> candidates = knowledgeStoreRepository.findCandidatesForSimilarity(
                currentIncident.getService(),
                currentIncident.getExceptionTypes(),
                currentIncident.getIncidentId()
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Score each candidate
        List<ScoredMatch> scored = new ArrayList<>();
        for (IncidentAnalysis candidate : candidates) {
            double score = calculateSimilarityScore(candidate, currentIncident);

            if (score >= MINIMUM_THRESHOLD) {
                Map<String, String> reasons = buildMatchReasons(candidate, currentIncident);
                scored.add(new ScoredMatch(
                        candidate.getIncidentId(),
                        candidate.getService(),
                        candidate.getAnalysisDate(),
                        score,
                        candidate.getRootCause(),
                        reasons
                ));
            }
        }

        // Sort by score descending, take top N
        scored.sort(Comparator.comparingDouble(ScoredMatch::getSimilarityScore).reversed());
        return scored.stream().limit(maxResults).collect(Collectors.toList());
    }

    /**
     * Calculate similarity score between two incidents.
     * Returns a value in [0.0, 1.0].
     */
    public double calculateSimilarityScore(IncidentAnalysis candidate, IncidentAnalysis current) {
        double score = 0.0;

        score += SERVICE_WEIGHT * serviceMatchScore(candidate.getService(), current.getService());
        score += EXCEPTION_TYPE_WEIGHT * exceptionTypeOverlap(candidate.getExceptionTypes(), current.getExceptionTypes());
        score += EVENT_CATEGORY_WEIGHT * eventCategoryOverlap(candidate.getExceptionCounts(), current.getExceptionCounts());
        score += ERROR_DISTRIBUTION_WEIGHT * errorDistributionSimilarity(candidate.getErrorDistribution(), current.getErrorDistribution());

        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * Service match: 1.0 for exact match, 0.0 otherwise.
     */
    public double serviceMatchScore(String service1, String service2) {
        if (service1 == null || service2 == null) return 0.0;
        return service1.equalsIgnoreCase(service2) ? 1.0 : 0.0;
    }

    /**
     * Exception type overlap using Jaccard index: |intersection| / |union|.
     * Returns 1.0 if both sets are empty.
     * Returns 0.0 if one set is empty and the other is not.
     */
    public double exceptionTypeOverlap(Set<String> types1, Set<String> types2) {
        Set<String> s1 = types1 != null ? types1 : Set.of();
        Set<String> s2 = types2 != null ? types2 : Set.of();

        if (s1.isEmpty() && s2.isEmpty()) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);

        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Event category overlap: normalized overlap of exception count keys.
     * Uses Jaccard index on the key sets of exception counts.
     */
    public double eventCategoryOverlap(Map<String, Integer> counts1, Map<String, Integer> counts2) {
        Set<String> keys1 = counts1 != null ? counts1.keySet() : Set.of();
        Set<String> keys2 = counts2 != null ? counts2.keySet() : Set.of();

        if (keys1.isEmpty() && keys2.isEmpty()) return 1.0;
        if (keys1.isEmpty() || keys2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(keys1);
        intersection.retainAll(keys2);

        Set<String> union = new HashSet<>(keys1);
        union.addAll(keys2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Error distribution similarity using cosine similarity over percentage vectors.
     * Returns 1.0 if both maps are empty.
     * Returns 0.0 if one is empty and the other is not.
     */
    public double errorDistributionSimilarity(Map<String, Double> pct1, Map<String, Double> pct2) {
        Map<String, Double> d1 = pct1 != null ? pct1 : Map.of();
        Map<String, Double> d2 = pct2 != null ? pct2 : Map.of();

        if (d1.isEmpty() && d2.isEmpty()) return 1.0;
        if (d1.isEmpty() || d2.isEmpty()) return 0.0;

        // Build union of all keys
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(d1.keySet());
        allKeys.addAll(d2.keySet());

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String key : allKeys) {
            double v1 = d1.getOrDefault(key, 0.0);
            double v2 = d2.getOrDefault(key, 0.0);
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Build human-readable match reasons explaining which factors contributed.
     */
    public Map<String, String> buildMatchReasons(IncidentAnalysis candidate, IncidentAnalysis current) {
        Map<String, String> reasons = new LinkedHashMap<>();

        double serviceScore = serviceMatchScore(candidate.getService(), current.getService());
        if (serviceScore > 0) {
            reasons.put("service", String.format("Same service: %s", candidate.getService()));
        }

        double exceptionOverlap = exceptionTypeOverlap(candidate.getExceptionTypes(), current.getExceptionTypes());
        if (exceptionOverlap > 0) {
            Set<String> shared = new HashSet<>(candidate.getExceptionTypes() != null ? candidate.getExceptionTypes() : Set.of());
            shared.retainAll(current.getExceptionTypes() != null ? current.getExceptionTypes() : Set.of());
            reasons.put("exceptionTypes", String.format("%.0f%% exception overlap (%s)", exceptionOverlap * 100, shared));
        }

        double categoryOverlap = eventCategoryOverlap(candidate.getExceptionCounts(), current.getExceptionCounts());
        if (categoryOverlap > 0) {
            reasons.put("eventCategory", String.format("%.0f%% event category overlap", categoryOverlap * 100));
        }

        double distributionSim = errorDistributionSimilarity(candidate.getErrorDistribution(), current.getErrorDistribution());
        if (distributionSim > 0.1) {
            reasons.put("errorDistribution", String.format("%.0f%% error distribution similarity", distributionSim * 100));
        }

        // Ensure at least one reason
        if (reasons.isEmpty()) {
            reasons.put("general", "Partial match on multiple factors");
        }

        return reasons;
    }
}
