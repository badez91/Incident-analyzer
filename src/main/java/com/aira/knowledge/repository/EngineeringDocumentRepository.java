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

    /**
     * Content-based similarity search.
     * Primary signal: full-text search on description/RCA content (searchable_text).
     * Secondary signals: exception type match, service match, recency.
     *
     * This finds tickets that are ACTUALLY about the same issue based on their
     * content — not just tickets from the same service.
     */
    @Query(value = """
            SELECT *,
                ts_rank_cd(search_vector, query) AS text_rank
            FROM engineering_document,
                 plainto_tsquery('english', :textQuery) AS query
            WHERE search_vector @@ query
              AND (:excludeRef = '' OR reference_id IS NULL OR reference_id != :excludeRef)
            ORDER BY text_rank DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EngineeringDocumentEntity> findByTextSimilarity(
            @Param("textQuery") String textQuery,
            @Param("excludeRef") String excludeReferenceId,
            @Param("limit") int limit
    );

    /**
     * Find similar by exception type — tickets with the same root exception
     * are highly likely to be related issues.
     */
    @Query(value = """
            SELECT * FROM engineering_document
            WHERE exception_type = :exception
              AND exception_type IS NOT NULL
              AND exception_type != ''
              AND (:excludeRef = '' OR reference_id IS NULL OR reference_id != :excludeRef)
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EngineeringDocumentEntity> findByExceptionType(
            @Param("exception") String exception,
            @Param("excludeRef") String excludeReferenceId,
            @Param("limit") int limit
    );

    /**
     * Simple metadata search (for the /search endpoint).
     */
    @Query(value = """
            SELECT * FROM engineering_document
            WHERE (
                (:service != '' AND service_name = :service)
                OR (:exception != '' AND exception_type = :exception)
            )
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EngineeringDocumentEntity> searchByMetadata(
            @Param("service") String service,
            @Param("exception") String exception,
            @Param("limit") int limit
    );
}
