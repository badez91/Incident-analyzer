package com.aira.incident.service;

import com.aira.incident.entity.IncidentEntity;
import com.aira.incident.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository repository;

    @InjectMocks
    private IncidentService incidentService;

    private IncidentEntity savedEntity;

    @BeforeEach
    void setUp() {
        savedEntity = new IncidentEntity();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setServiceName("payment-service");
        savedEntity.setSeverity("HIGH");
        savedEntity.setStatus("INGESTED");
    }

    @Test
    void createIncident_savesWithAllFields() {
        when(repository.save(any(IncidentEntity.class))).thenReturn(savedEntity);

        IncidentEntity result = incidentService.createIncident(
                "payment-service", "HIGH", "Timeout errors", "MANUAL", "CM-123",
                Map.of("env", "prod"));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getServiceName()).isEqualTo("payment-service");
        verify(repository).save(any(IncidentEntity.class));
    }

    @Test
    void createIncident_handlesNullOptionalFields() {
        when(repository.save(any(IncidentEntity.class))).thenReturn(savedEntity);

        IncidentEntity result = incidentService.createIncident(
                "auth-service", null, null, null, null, null);

        assertThat(result).isNotNull();
        verify(repository).save(any(IncidentEntity.class));
    }

    @Test
    void updateStatus_updatesWhenFound() {
        when(repository.findById(savedEntity.getId())).thenReturn(Optional.of(savedEntity));
        when(repository.save(any(IncidentEntity.class))).thenReturn(savedEntity);

        Optional<IncidentEntity> result = incidentService.updateStatus(savedEntity.getId(), "RESOLVED");

        assertThat(result).isPresent();
        verify(repository).save(any(IncidentEntity.class));
    }

    @Test
    void updateStatus_returnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        Optional<IncidentEntity> result = incidentService.updateStatus(id, "RESOLVED");

        assertThat(result).isEmpty();
    }

    @Test
    void findById_delegatesToRepository() {
        when(repository.findById(savedEntity.getId())).thenReturn(Optional.of(savedEntity));

        Optional<IncidentEntity> result = incidentService.findById(savedEntity.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getServiceName()).isEqualTo("payment-service");
    }
}
