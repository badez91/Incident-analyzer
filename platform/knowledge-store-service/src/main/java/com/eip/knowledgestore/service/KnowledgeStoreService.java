package com.eip.knowledgestore.service;

import com.eip.common.model.CanonicalEvent;
import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.Evidence;
import com.eip.common.model.IncidentAnalysisResult;
import com.eip.common.model.RecommendedActions;
import com.eip.knowledgestore.entity.IncidentAnalysisEntity;
import com.eip.knowledgestore.entity.IncidentEntity;
import com.eip.knowledgestore.repository.IncidentAnalysisJpaRepository;
import com.eip.knowledgestore.repository.IncidentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class KnowledgeStoreService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStoreService.class);
    private static final int BATCH_SIZE = 1000;

    private final IncidentJpaRepository incidentRepository;
    private final IncidentAnalysisJpaRepository analysisRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public KnowledgeStoreService(IncidentJpaRepository incidentRepository,
                                  IncidentAnalysisJpaRepository analysisRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.incidentRepository = incidentRepository;
        this.analysisRepository = analysisRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IncidentEntity storeIncident(CanonicalIncidentEvent event) {
        IncidentEntity entity = mapToIncidentEntity(event);
        IncidentEntity saved = incidentRepository.save(entity);

        if (event.events() != null && !event.events().isEmpty()) {
            batchInsertCanonicalEvents(saved.getId(), event.events());
        }

        log.info("Stored incident {} with {} events", saved.getId(), 
                event.events() != null ? event.events().size() : 0);
        return saved;
    }

    @Transactional
    public IncidentAnalysisEntity storeAnalysis(IncidentAnalysisResult result) {
        IncidentAnalysisEntity entity = mapToAnalysisEntity(result);
        IncidentAnalysisEntity saved = analysisRepository.save(entity);
        log.info("Stored analysis {} for incident {}", saved.getId(), saved.getIncidentId());
        return saved;
    }

    public Optional<IncidentEntity> findIncidentById(UUID id) {
        return incidentRepository.findById(id);
    }

    public Optional<IncidentAnalysisEntity> findAnalysisById(UUID id) {
        return analysisRepository.findById(id);
    }

    public Page<IncidentEntity> findIncidents(String service, String status, Pageable pageable) {
        if (service != null && status != null) {
            return incidentRepository.findByServiceNameAndStatus(service, status, pageable);
        } else if (service != null) {
            return incidentRepository.findByServiceName(service, pageable);
        } else if (status != null) {
            return incidentRepository.findByStatus(status, pageable);
        }
        return incidentRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<IncidentEntity> findCandidates(String service, String[] exceptionTypes, UUID excludeId) {
        return incidentRepository.findCandidatesForSimilarity(service, exceptionTypes, excludeId);
    }

    private IncidentEntity mapToIncidentEntity(CanonicalIncidentEvent event) {
        IncidentEntity entity = new IncidentEntity();
        entity.setId(event.incidentId() != null ? event.incidentId() : UUID.randomUUID());
        entity.setServiceName(event.serviceName());
        entity.setSeverity(event.severity() != null ? event.severity() : "MEDIUM");
        entity.setSource(event.source() != null ? event.source() : "API");
        entity.setStatus("INGESTED");
        entity.setSymptoms(event.symptoms());
        entity.setRawPayload(event.rawPayload());
        entity.setExceptionCounts(event.exceptionCounts());
        entity.setExceptionTypes(event.exceptionTypes() != null
                ? event.exceptionTypes().toArray(new String[0])
                : new String[0]);
        entity.setErrorDistribution(event.errorDistribution());
        entity.setTotalEvents(event.totalEvents());
        entity.setTotalExceptions(event.totalExceptions());
        return entity;
    }

    private IncidentAnalysisEntity mapToAnalysisEntity(IncidentAnalysisResult result) {
        IncidentAnalysisEntity entity = new IncidentAnalysisEntity();
        entity.setId(result.analysisId() != null ? result.analysisId() : UUID.randomUUID());
        entity.setIncidentId(result.incidentId());
        entity.setCategory(result.category());
        entity.setRootCause(result.rootCause());
        entity.setSeverity(result.severity());
        entity.setConfidence(result.confidence());
        entity.setConfidencePercent(result.confidencePercent());
        entity.setSummary(result.summary());
        entity.setBusinessImpact(result.businessImpact());

        if (result.recommendations() != null) {
            Map<String, Object> recsMap = new HashMap<>();
            recsMap.put("immediate", result.recommendations().immediate());
            recsMap.put("shortTerm", result.recommendations().shortTerm());
            recsMap.put("longTerm", result.recommendations().longTerm());
            entity.setRecommendations(recsMap);
        }

        if (result.evidence() != null) {
            List<Map<String, Object>> evidenceList = new ArrayList<>();
            for (Evidence e : result.evidence()) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", e.type());
                map.put("content", e.content());
                map.put("relevance", e.relevance());
                evidenceList.add(map);
            }
            entity.setEvidence(evidenceList);
        }

        entity.setLlmModel(result.llmModel());
        entity.setLlmProvider(result.llmProvider());
        entity.setInferenceTimeMs(result.inferenceTimeMs());
        entity.setStatus(result.status() != null ? result.status() : "COMPLETE");
        entity.setAnalyzedAt(result.analyzedAt() != null ? result.analyzedAt() : Instant.now());
        return entity;
    }

    private void batchInsertCanonicalEvents(UUID incidentId, List<CanonicalEvent> events) {
        String sql = """
                INSERT INTO canonical_event (id, incident_id, timestamp, level, service, event_type, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, events, BATCH_SIZE, (PreparedStatement ps, CanonicalEvent event) -> {
            ps.setObject(1, event.eventId() != null ? event.eventId() : UUID.randomUUID());
            ps.setObject(2, incidentId);
            ps.setTimestamp(3, event.timestamp() != null ? Timestamp.from(event.timestamp()) : null);
            ps.setString(4, event.level());
            ps.setString(5, event.service());
            ps.setString(6, event.eventType());
            ps.setString(7, event.message());
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
        });

        log.debug("Batch inserted {} canonical events for incident {}", events.size(), incidentId);
    }
}
