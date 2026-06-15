package com.eip.knowledgestore.controller;

import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.IncidentAnalysisResult;
import com.eip.knowledgestore.entity.IncidentAnalysisEntity;
import com.eip.knowledgestore.entity.IncidentEntity;
import com.eip.knowledgestore.service.KnowledgeStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeStoreController {

    private final KnowledgeStoreService knowledgeStoreService;

    @Autowired
    public KnowledgeStoreController(KnowledgeStoreService knowledgeStoreService) {
        this.knowledgeStoreService = knowledgeStoreService;
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentEntity> storeIncident(@RequestBody CanonicalIncidentEvent event) {
        IncidentEntity saved = knowledgeStoreService.storeIncident(event);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<IncidentEntity> getIncident(@PathVariable UUID id) {
        return knowledgeStoreService.findIncidentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/incidents")
    public ResponseEntity<Page<IncidentEntity>> getIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status) {

        int effectiveSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, effectiveSize);
        Page<IncidentEntity> incidents = knowledgeStoreService.findIncidents(service, status, pageable);
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<IncidentEntity>> getCandidates(
            @RequestParam String service,
            @RequestParam List<String> exceptionTypes,
            @RequestParam UUID excludeId) {

        String[] typesArray = exceptionTypes.toArray(new String[0]);
        List<IncidentEntity> candidates = knowledgeStoreService.findCandidates(service, typesArray, excludeId);
        return ResponseEntity.ok(candidates);
    }

    @PostMapping("/analyses")
    public ResponseEntity<IncidentAnalysisEntity> storeAnalysis(@RequestBody IncidentAnalysisResult result) {
        IncidentAnalysisEntity saved = knowledgeStoreService.storeAnalysis(result);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/analyses/{id}")
    public ResponseEntity<IncidentAnalysisEntity> getAnalysis(@PathVariable UUID id) {
        return knowledgeStoreService.findAnalysisById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "knowledge-store-service",
                "status", "UP"
        ));
    }
}
