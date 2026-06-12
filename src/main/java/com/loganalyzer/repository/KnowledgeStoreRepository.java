package com.loganalyzer.repository;

import com.loganalyzer.entity.CanonicalEventEntity;
import com.loganalyzer.entity.IncidentAnalysisEntity;
import com.loganalyzer.model.CanonicalEvent;
import com.loganalyzer.model.IncidentAnalysis;
import com.loganalyzer.service.DataModelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data access layer for PostgreSQL-backed incident knowledge storage.
 * Provides CRUD, batch inserts, and similarity query operations.
 */
@Repository
public class KnowledgeStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStoreRepository.class);
    private static final int BATCH_SIZE = 1000;

    private final IncidentAnalysisJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataModelValidator validator;

    public KnowledgeStoreRepository(IncidentAnalysisJpaRepository jpaRepository,
                                    JdbcTemplate jdbcTemplate,
                                    DataModelValidator validator) {
        this.jpaRepository = jpaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.validator = validator;
    }

    /**
     * Save an incident analysis. Validates before persisting.
     */
    @Transactional
    public IncidentAnalysis save(IncidentAnalysis analysis) {
        validator.validate(analysis);

        IncidentAnalysisEntity entity = toEntity(analysis);
        jpaRepository.save(entity);

        log.info("Persisted incident analysis: {}", analysis.getIncidentId());
        return analysis;
    }

    /**
     * Save canonical events in batches of 1000, linked to an incident.
     */
    @Transactional
    public void saveEvents(UUID incidentId, List<CanonicalEvent> events) {
        if (events == null || events.isEmpty()) return;

        String sql = """
                INSERT INTO canonical_events (id, incident_id, timestamp, level, service, event_type, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        List<List<CanonicalEvent>> batches = partition(events, BATCH_SIZE);

        for (List<CanonicalEvent> batch : batches) {
            List<Object[]> batchArgs = batch.stream()
                    .map(event -> new Object[]{
                            event.getId() != null ? event.getId() : UUID.randomUUID(),
                            incidentId,
                            event.getTimestamp() != null ? Timestamp.from(event.getTimestamp()) : null,
                            event.getLevel(),
                            event.getService(),
                            event.getEventType(),
                            event.getMessage(),
                            Timestamp.from(Instant.now())
                    })
                    .collect(Collectors.toList());

            jdbcTemplate.batchUpdate(sql, batchArgs);
        }

        log.info("Persisted {} canonical events for incident {}", events.size(), incidentId);
    }

    /**
     * Save an incident analysis and its events in a single transaction.
     */
    @Transactional
    public IncidentAnalysis saveWithEvents(IncidentAnalysis analysis, List<CanonicalEvent> events) {
        IncidentAnalysis saved = save(analysis);
        saveEvents(analysis.getIncidentId(), events);
        return saved;
    }

    /**
     * Find an incident by ID.
     */
    public Optional<IncidentAnalysis> findById(UUID incidentId) {
        return jpaRepository.findById(incidentId).map(this::toDomain);
    }

    /**
     * Find all incidents, paginated and ordered by analysis date descending.
     */
    public List<IncidentAnalysis> findAll(int page, int size) {
        Page<IncidentAnalysisEntity> pageResult = jpaRepository
                .findAllByOrderByAnalysisDateDesc(PageRequest.of(page, size));
        return pageResult.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Find incidents by service name.
     */
    public List<IncidentAnalysis> findByService(String service) {
        return jpaRepository.findByServiceOrderByAnalysisDateDesc(service).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Find candidates for similarity matching.
     * Pre-filters by service match OR exception type overlap, excludes self, limits to 50.
     */
    public List<IncidentAnalysis> findCandidatesForSimilarity(String service,
                                                              Set<String> exceptionTypes,
                                                              UUID excludeId) {
        String[] typesArray = exceptionTypes != null
                ? exceptionTypes.toArray(new String[0])
                : new String[0];

        return jpaRepository.findCandidatesForSimilarity(service, typesArray, excludeId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Count total incidents in the store.
     */
    public long count() {
        return jpaRepository.count();
    }

    // --- Mapping: Domain -> Entity ---

    private IncidentAnalysisEntity toEntity(IncidentAnalysis analysis) {
        IncidentAnalysisEntity entity = new IncidentAnalysisEntity();
        entity.setIncidentId(analysis.getIncidentId());
        entity.setAnalysisDate(analysis.getAnalysisDate());
        entity.setService(analysis.getService() != null ? analysis.getService() : "UNKNOWN");
        entity.setSourceFilename(analysis.getSourceFilename());
        entity.setTimeRangeStart(analysis.getTimeRangeStart());
        entity.setTimeRangeEnd(analysis.getTimeRangeEnd());
        entity.setTotalEvents(analysis.getTotalEvents());
        entity.setTotalExceptions(analysis.getTotalExceptions());
        entity.setUniqueExceptionTypes(analysis.getUniqueExceptionTypes());
        entity.setExceptionCounts(analysis.getExceptionCounts());
        entity.setExceptionTypes(analysis.getExceptionTypes() != null
                ? analysis.getExceptionTypes().toArray(new String[0])
                : new String[0]);
        entity.setErrorDistribution(analysis.getErrorDistribution());
        entity.setRootCause(analysis.getRootCause());
        entity.setImpact(analysis.getImpact());
        entity.setRecommendations(analysis.getRecommendations());
        entity.setSeverity(analysis.getSeverity());
        entity.setConfidence(analysis.getConfidence());
        entity.setConfidencePercent(analysis.getConfidencePercent());
        entity.setSummary(analysis.getSummary());
        entity.setRawResponse(analysis.getRawResponse());
        entity.setStatus(analysis.getStatus());
        entity.setErrorMessage(analysis.getErrorMessage());

        // Convert AiAnalysis to Map for JSONB storage
        if (analysis.getLlmAnalysis() != null) {
            Map<String, Object> llmMap = new LinkedHashMap<>();
            llmMap.put("severity", analysis.getLlmAnalysis().getSeverity());
            llmMap.put("confidence", analysis.getLlmAnalysis().getConfidence());
            llmMap.put("confidencePercent", analysis.getLlmAnalysis().getConfidencePercent());
            llmMap.put("rootCause", analysis.getLlmAnalysis().getRootCause());
            llmMap.put("businessImpact", analysis.getLlmAnalysis().getBusinessImpact());
            llmMap.put("summary", analysis.getLlmAnalysis().getSummary());
            if (analysis.getLlmAnalysis().getRecommendedActions() != null) {
                Map<String, Object> actions = new LinkedHashMap<>();
                actions.put("immediate", analysis.getLlmAnalysis().getRecommendedActions().getImmediate());
                actions.put("shortTerm", analysis.getLlmAnalysis().getRecommendedActions().getShortTerm());
                actions.put("longTerm", analysis.getLlmAnalysis().getRecommendedActions().getLongTerm());
                llmMap.put("recommendedActions", actions);
            }
            entity.setLlmAnalysis(llmMap);
        }

        return entity;
    }

    // --- Mapping: Entity -> Domain ---

    private IncidentAnalysis toDomain(IncidentAnalysisEntity entity) {
        IncidentAnalysis analysis = new IncidentAnalysis();
        analysis.setIncidentId(entity.getIncidentId());
        analysis.setAnalysisDate(entity.getAnalysisDate());
        analysis.setService(entity.getService());
        analysis.setSourceFilename(entity.getSourceFilename());
        analysis.setTimeRangeStart(entity.getTimeRangeStart());
        analysis.setTimeRangeEnd(entity.getTimeRangeEnd());
        analysis.setTotalEvents(entity.getTotalEvents());
        analysis.setTotalExceptions(entity.getTotalExceptions());
        analysis.setUniqueExceptionTypes(entity.getUniqueExceptionTypes());
        analysis.setExceptionCounts(entity.getExceptionCounts());
        analysis.setExceptionTypes(entity.getExceptionTypes() != null
                ? new LinkedHashSet<>(Arrays.asList(entity.getExceptionTypes()))
                : new LinkedHashSet<>());
        analysis.setErrorDistribution(entity.getErrorDistribution());
        analysis.setRootCause(entity.getRootCause());
        analysis.setImpact(entity.getImpact());
        analysis.setRecommendations(entity.getRecommendations());
        analysis.setSeverity(entity.getSeverity());
        analysis.setConfidence(entity.getConfidence());
        analysis.setConfidencePercent(entity.getConfidencePercent());
        analysis.setSummary(entity.getSummary());
        analysis.setRawResponse(entity.getRawResponse());
        analysis.setStatus(entity.getStatus());
        analysis.setErrorMessage(entity.getErrorMessage());

        return analysis;
    }

    // --- Utility ---

    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}
