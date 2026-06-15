package com.eip.analysis.controller;

import com.eip.analysis.client.KnowledgeStoreClient;
import com.eip.analysis.service.AnalysisPipelineService;
import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.IncidentAnalysisResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisPipelineService pipelineService;
    private final KnowledgeStoreClient knowledgeStoreClient;

    public AnalysisController(AnalysisPipelineService pipelineService, KnowledgeStoreClient knowledgeStoreClient) {
        this.pipelineService = pipelineService;
        this.knowledgeStoreClient = knowledgeStoreClient;
    }

    @PostMapping
    public ResponseEntity<IncidentAnalysisResult> analyze(
            @RequestBody CanonicalIncidentEvent incident,
            @RequestParam(value = "model", required = false) String model) {
        IncidentAnalysisResult result = pipelineService.analyze(incident, model);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentAnalysisResult> getAnalysis(@PathVariable UUID incidentId) {
        IncidentAnalysisResult result = knowledgeStoreClient.getAnalysis(incidentId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
