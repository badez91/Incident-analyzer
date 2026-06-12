package com.loganalyzer.repository;

import com.loganalyzer.entity.IncidentAnalysisEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for incident_analyses table.
 */
@Repository
public interface IncidentAnalysisJpaRepository extends JpaRepository<IncidentAnalysisEntity, UUID> {

    Page<IncidentAnalysisEntity> findAllByOrderByAnalysisDateDesc(Pageable pageable);

    List<IncidentAnalysisEntity> findByServiceOrderByAnalysisDateDesc(String service);

    /**
     * Find candidates for similarity matching:
     * - Match by service OR overlapping exception types
     * - Exclude the current incident
     * - Limit to 50 most recent
     */
    @Query(value = """
            SELECT * FROM incident_analyses
            WHERE incident_id != :excludeId
              AND (service = :service OR exception_types && CAST(:exceptionTypes AS text[]))
            ORDER BY analysis_date DESC
            LIMIT 50
            """, nativeQuery = true)
    List<IncidentAnalysisEntity> findCandidatesForSimilarity(
            @Param("service") String service,
            @Param("exceptionTypes") String[] exceptionTypes,
            @Param("excludeId") UUID excludeId);
}
