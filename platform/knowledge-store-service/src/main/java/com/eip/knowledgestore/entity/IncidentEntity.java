package com.eip.knowledgestore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class IncidentEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "description")
    private String description;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "symptoms", columnDefinition = "jsonb")
    private List<String> symptoms;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exception_counts", columnDefinition = "jsonb")
    private Map<String, Integer> exceptionCounts;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "exception_types", columnDefinition = "text[]")
    private String[] exceptionTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_distribution", columnDefinition = "jsonb")
    private Map<String, Double> errorDistribution;

    @Column(name = "total_events")
    private int totalEvents;

    @Column(name = "total_exceptions")
    private int totalExceptions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(List<String> symptoms) {
        this.symptoms = symptoms;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }

    public Map<String, Integer> getExceptionCounts() {
        return exceptionCounts;
    }

    public void setExceptionCounts(Map<String, Integer> exceptionCounts) {
        this.exceptionCounts = exceptionCounts;
    }

    public String[] getExceptionTypes() {
        return exceptionTypes;
    }

    public void setExceptionTypes(String[] exceptionTypes) {
        this.exceptionTypes = exceptionTypes;
    }

    public Map<String, Double> getErrorDistribution() {
        return errorDistribution;
    }

    public void setErrorDistribution(Map<String, Double> errorDistribution) {
        this.errorDistribution = errorDistribution;
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(int totalEvents) {
        this.totalEvents = totalEvents;
    }

    public int getTotalExceptions() {
        return totalExceptions;
    }

    public void setTotalExceptions(int totalExceptions) {
        this.totalExceptions = totalExceptions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
