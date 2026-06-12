package com.loganalyzer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * JPA entity mapped to the incident_analyses table.
 */
@Entity
@Table(name = "incident_analyses")
public class IncidentAnalysisEntity {

    @Id
    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "analysis_date", nullable = false)
    private Instant analysisDate;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "time_range_start")
    private Instant timeRangeStart;

    @Column(name = "time_range_end")
    private Instant timeRangeEnd;

    @Column(name = "total_events", nullable = false)
    private int totalEvents;

    @Column(name = "total_exceptions", nullable = false)
    private int totalExceptions;

    @Column(name = "unique_exception_types", nullable = false)
    private int uniqueExceptionTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exception_counts", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> exceptionCounts = new LinkedHashMap<>();

    @Column(name = "exception_types", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] exceptionTypes = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_distribution", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> errorDistribution = new LinkedHashMap<>();

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(name = "impact", columnDefinition = "text")
    private String impact;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_analysis", columnDefinition = "jsonb")
    private Map<String, Object> llmAnalysis;

    @Column(name = "severity")
    private String severity;

    @Column(name = "confidence")
    private String confidence;

    @Column(name = "confidence_percent")
    private int confidencePercent;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "status", nullable = false)
    private String status = "COMPLETE";

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
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

    public String[] getExceptionTypes() { return exceptionTypes; }
    public void setExceptionTypes(String[] exceptionTypes) { this.exceptionTypes = exceptionTypes; }

    public Map<String, Double> getErrorDistribution() { return errorDistribution; }
    public void setErrorDistribution(Map<String, Double> errorDistribution) { this.errorDistribution = errorDistribution; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

    public Map<String, Object> getLlmAnalysis() { return llmAnalysis; }
    public void setLlmAnalysis(Map<String, Object> llmAnalysis) { this.llmAnalysis = llmAnalysis; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
