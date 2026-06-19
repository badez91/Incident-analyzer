package com.aira.knowledge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "engineering_document")
@Getter
@Setter
@NoArgsConstructor
public class EngineeringDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(length = 500)
    private String summary;

    @Column(name = "service_name")
    private String serviceName;

    private String environment;

    @Column(name = "exception_type")
    private String exceptionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String components;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_metadata", columnDefinition = "jsonb")
    private String structuredMetadata;

    @Column(name = "searchable_text", columnDefinition = "text")
    private String searchableText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
