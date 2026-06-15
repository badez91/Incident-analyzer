package com.eip.knowledgestore.repository;

import com.eip.knowledgestore.entity.IncidentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentJpaRepository extends JpaRepository<IncidentEntity, UUID> {

    Page<IncidentEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<IncidentEntity> findByServiceName(String serviceName);

    @Query(value = """
            SELECT * FROM incident
            WHERE service_name = :service
              AND exception_types && CAST(:exceptionTypes AS text[])
              AND id != :excludeId
            ORDER BY created_at DESC
            LIMIT 50
            """, nativeQuery = true)
    List<IncidentEntity> findCandidatesForSimilarity(
            @Param("service") String service,
            @Param("exceptionTypes") String[] exceptionTypes,
            @Param("excludeId") UUID excludeId
    );

    Page<IncidentEntity> findByServiceNameAndStatus(String serviceName, String status, Pageable pageable);

    Page<IncidentEntity> findByServiceName(String serviceName, Pageable pageable);

    Page<IncidentEntity> findByStatus(String status, Pageable pageable);
}
