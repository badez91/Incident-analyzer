package com.eip.knowledgestore.repository;

import com.eip.knowledgestore.entity.IncidentAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentAnalysisJpaRepository extends JpaRepository<IncidentAnalysisEntity, UUID> {

    List<IncidentAnalysisEntity> findByIncidentId(UUID incidentId);
}
