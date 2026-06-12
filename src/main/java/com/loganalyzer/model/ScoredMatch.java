package com.loganalyzer.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A record representing a historical incident with its computed similarity score
 * and match reasoning.
 */
public class ScoredMatch {

    private UUID incidentId;
    private String service;
    private Instant analysisDate;
    private double similarityScore; // 0.0 to 1.0
    private String rootCause;
    private Map<String, String> matchReasons; // factor -> explanation

    public ScoredMatch() {}

    public ScoredMatch(UUID incidentId, String service, Instant analysisDate,
                       double similarityScore, String rootCause, Map<String, String> matchReasons) {
        this.incidentId = incidentId;
        this.service = service;
        this.analysisDate = analysisDate;
        this.similarityScore = similarityScore;
        this.rootCause = rootCause;
        this.matchReasons = matchReasons;
    }

    // --- Getters and Setters ---

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public Instant getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(Instant analysisDate) { this.analysisDate = analysisDate; }

    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public Map<String, String> getMatchReasons() { return matchReasons; }
    public void setMatchReasons(Map<String, String> matchReasons) { this.matchReasons = matchReasons; }
}
