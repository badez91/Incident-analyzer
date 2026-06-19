package com.aira.knowledge.repository;

import com.aira.knowledge.entity.EngineeringDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngineeringDocumentRepository extends JpaRepository<EngineeringDocumentEntity, UUID> {

    List<EngineeringDocumentEntity> findByServiceName(String serviceName);

    List<EngineeringDocumentEntity> findBySourceType(String sourceType);

    Optional<EngineeringDocumentEntity> findByReferenceId(String referenceId);

    @Query(value = """
            SELECT * FROM engineering_document
            WHERE (service_name = :service OR exception_type = :exception)
              AND id != :excludeId
            ORDER BY created_at DESC
            LIMIT 10
            """, nativeQuery = true)
    List<EngineeringDocumentEntity> findSimilarByMetadata(
            @Param("service") String service,
            @Param("exception") String exception,
            @Param("excludeId") UUID excludeId
    );

    @Query(value = """
            SELECT * FROM engineering_document
            WHERE (service_name = :service OR exception_type = :exception)
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EngineeringDocumentEntity> searchByMetadata(
            @Param("service") String service,
            @Param("exception") String exception,
            @Param("limit") int limit
    );
}
