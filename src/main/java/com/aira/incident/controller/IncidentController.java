package com.aira.incident.controller;

import com.aira.incident.entity.IncidentEntity;
import com.aira.incident.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<IncidentEntity> create(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentEntity entity = incidentService.createIncident(
                request.serviceName(),
                request.severity(),
                request.summary(),
                request.source(),
                request.jiraKey(),
                request.metadata()
        );
        return ResponseEntity.ok(entity);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentEntity> getById(@PathVariable UUID id) {
        return incidentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<IncidentEntity>> search(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status) {

        List<IncidentEntity> results = incidentService.search(service, status);
        return ResponseEntity.ok(results);
    }

    public record CreateIncidentRequest(
            @NotBlank(message = "serviceName is required")
            String serviceName,
            String severity,
            String summary,
            String source,
            String jiraKey,
            Map<String, Object> metadata
    ) {}
}
