package com.aira.incident.service;

import com.aira.incident.entity.IncidentEntity;
import com.aira.incident.repository.IncidentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IncidentRepository repository;

    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    public IncidentEntity createIncident(String serviceName, String severity, String summary,
                                         String source, String jiraKey, Map<String, Object> metadata) {
        IncidentEntity entity = new IncidentEntity();
        entity.setServiceName(serviceName);
        if (severity != null) entity.setSeverity(severity);
        entity.setSummary(summary);
        if (source != null) entity.setSource(source);
        entity.setJiraKey(jiraKey);

        if (metadata != null && !metadata.isEmpty()) {
            try {
                entity.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.warn("Failed to serialize metadata: {}", e.getMessage());
            }
        }

        IncidentEntity saved = repository.save(entity);
        log.info("Created incident: id={}, service={}, severity={}", saved.getId(), serviceName, severity);
        return saved;
    }

    public Optional<IncidentEntity> updateStatus(UUID id, String newStatus) {
        return repository.findById(id).map(entity -> {
            entity.setStatus(newStatus);
            IncidentEntity updated = repository.save(entity);
            log.info("Updated incident status: id={}, newStatus={}", id, newStatus);
            return updated;
        });
    }

    public Optional<IncidentEntity> findById(UUID id) {
        return repository.findById(id);
    }

    public List<IncidentEntity> search(String service, String status) {
        if (service != null && status != null) {
            return repository.findByServiceName(service).stream()
                    .filter(i -> i.getStatus().equalsIgnoreCase(status))
                    .toList();
        } else if (service != null) {
            return repository.findByServiceName(service);
        } else if (status != null) {
            return repository.findByStatus(status);
        }
        return repository.findAll();
    }
}
