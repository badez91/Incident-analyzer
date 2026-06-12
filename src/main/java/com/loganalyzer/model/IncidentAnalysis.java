package com.loganalyzer.model;

import java.time.Instant;
import java.util.*;

/**
 * A structured record representing a complete analysis of a log file,
 * including error summary, exception counts, LLM-generated root cause,
 * and metadata for similarity matching.
 */
public class IncidentAnalysis {

    private UUID incidentId;
    private Instant analysisDate;
    private String service;
    private String sourceFilename;
    private Instant timeRangeStart;
    private Instant timeRangeEnd;
    private int totalEvents;
    private int totalExceptions;
    private int uniqueExceptionTypes;
    private Map<String, Integer> exceptionCounts;
    private Set<String> exceptionTypes;
    private Map<String, Double> errorDistribution;
    private String rootCause;
    private String impact;
    private List<String> recommendations;
    private AiAnalysis llmAnalysis;
    private String severity;
    private String confidence;
    private int confidencePercent;
    private String summary;
    private String rawResponse;
    private String status; // COMPLETE, INCOMPLETE, FAILED
    private String errorMessage;

    public IncidentAnalysis() {
        this.incidentId = UUID.randomUUID();
        this.analysisDate = Instant.now();
        this.status = "COMPLETE";
        this.exceptionCounts = new LinkedHashMap<>();
        this.exceptionTypes = new LinkedHashSet<>();
        this.errorDistribution = new LinkedHashMap<>();
        this.recommendations = new ArrayList<>();
    }

    // --- Getters and Setters ---

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public Instant getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(Instant analysisDate) { this.analysisDate = analysisDate; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }

    public Instant getTimeRangeStart() { return timeRangeStart; }
    public void setTimeRangeStart(Instant timeRangeStart) { this.timeRangeStart = timeRangeStart; }

    public Instant getTimeRangeEnd() { return timeRangeEnd; }
    public void setTimeRangeEnd(Instant timeRangeEnd) { this.timeRangeEnd = timeRangeEnd; }

    public int getTotalEvents() { return totalEvents; }
    public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }

    public int getTotalExceptions() { return totalExceptions; }
    public void setTotalExceptions(int totalExceptions) { this.totalExceptions = totalExceptions; }

    public int getUniqueExceptionTypes() { return uniqueExceptionTypes; }
    public void setUniqueExceptionTypes(int uniqueExceptionTypes) { this.uniqueExceptionTypes = uniqueExceptionTypes; }

    public Map<String, Integer> getExceptionCounts() { return exceptionCounts; }
    public void setExceptionCounts(Map<String, Integer> exceptionCounts) { this.exceptionCounts = exceptionCounts; }

    public Set<String> getExceptionTypes() { return exceptionTypes; }
    public void setExceptionTypes(Set<String> exceptionTypes) { this.exceptionTypes = exceptionTypes; }

    public Map<String, Double> getErrorDistribution() { return errorDistribution; }
    public void setErrorDistribution(Map<String, Double> errorDistribution) { this.errorDistribution = errorDistribution; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

    public AiAnalysis getLlmAnalysis() { return llmAnalysis; }
    public void setLlmAnalysis(AiAnalysis llmAnalysis) { this.llmAnalysis = llmAnalysis; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public int getConfidencePercent() { return confidencePercent; }
    public void setConfidencePercent(int confidencePercent) { this.confidencePercent = confidencePercent; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
