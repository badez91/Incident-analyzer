package com.aira.incident.repository;

import com.aira.incident.entity.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {

    List<IncidentEntity> findByServiceName(String serviceName);

    Optional<IncidentEntity> findByJiraKey(String jiraKey);

    List<IncidentEntity> findByStatus(String status);
}
