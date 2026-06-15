package com.eip.knowledgestore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "incident_analysis")
public class IncidentAnalysisEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "category")
    private String category;

    @Column(name = "root_cause")
    private String rootCause;

    @Column(name = "severity")
    private String severity;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "confidence_percent")
    private Integer confidencePercent;

    @Column(name = "summary")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_impact", columnDefinition = "jsonb")
    private List<String> businessImpact;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private Map<String, Object> recommendations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private List<Map<String, Object>> evidence;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "llm_provider")
    private String llmProvider;

    @Column(name = "inference_time_ms")
    private Long inferenceTimeMs;

    @Column(name = "raw_response")
    private String rawResponse;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getConfidencePercent() {
        return confidencePercent;
    }

    public void setConfidencePercent(Integer confidencePercent) {
        this.confidencePercent = confidencePercent;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getBusinessImpact() {
        return businessImpact;
    }

    public void setBusinessImpact(List<String> businessImpact) {
        this.businessImpact = businessImpact;
    }

    public Map<String, Object> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(Map<String, Object> recommendations) {
        this.recommendations = recommendations;
    }

    public List<Map<String, Object>> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<Map<String, Object>> evidence) {
        this.evidence = evidence;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    public Long getInferenceTimeMs() {
        return inferenceTimeMs;
    }

    public void setInferenceTimeMs(Long inferenceTimeMs) {
        this.inferenceTimeMs = inferenceTimeMs;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Instant analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}
