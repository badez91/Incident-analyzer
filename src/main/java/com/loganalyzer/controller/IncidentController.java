package com.loganalyzer.controller;

import com.loganalyzer.model.IncidentAnalysis;
import com.loganalyzer.model.ScoredMatch;
import com.loganalyzer.repository.KnowledgeStoreRepository;
import com.loganalyzer.service.SimilarityMatchingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller exposing endpoints for querying historical incident knowledge.
 */
@RestController
@RequestMapping("/incidents")
public class IncidentController {

    private final KnowledgeStoreRepository knowledgeStoreRepository;
    private final SimilarityMatchingService similarityMatchingService;

    public IncidentController(KnowledgeStoreRepository knowledgeStoreRepository,
                              SimilarityMatchingService similarityMatchingService) {
        this.knowledgeStoreRepository = knowledgeStoreRepository;
        this.similarityMatchingService = similarityMatchingService;
    }

    /**
     * GET /incidents — Paginated list of incident summaries.
     * Defaults: page=0, size=20, size max=100.
     */
    @GetMapping
    public ResponseEntity<?> listIncidents(
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {

        // Validate parameters
        int pageNum;
        int sizeNum;
        try {
            pageNum = Integer.parseInt(page);
            sizeNum = Integer.parseInt(size);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid parameter: page and size must be numeric values."));
        }

        if (pageNum < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid parameter: page must be >= 0."));
        }
        if (sizeNum < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid parameter: size must be >= 0."));
        }

        // Cap size at 100
        sizeNum = Math.min(sizeNum, 100);

        List<IncidentAnalysis> incidents = knowledgeStoreRepository.findAll(pageNum, sizeNum);

        List<IncidentSummaryDto> summaries = incidents.stream()
                .map(IncidentSummaryDto::from)
                .toList();

        return ResponseEntity.ok(Map.of(
                "page", pageNum,
                "size", sizeNum,
                "total", knowledgeStoreRepository.count(),
                "incidents", summaries
        ));
    }

    /**
     * GET /incidents/{id} — Full incident detail by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getIncident(@PathVariable String id) {
        UUID incidentId;
        try {
            incidentId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid incident ID format. Must be a valid UUID."));
        }

        Optional<IncidentAnalysis> incident = knowledgeStoreRepository.findById(incidentId);
        if (incident.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Incident not found: " + id));
        }

        return ResponseEntity.ok(IncidentDetailDto.from(incident.get()));
    }

    /**
     * GET /incidents/similar — Find similar incidents by incidentId.
     */
    @GetMapping("/similar")
    public ResponseEntity<?> findSimilar(
            @RequestParam(required = false) String incidentId,
            @RequestParam(defaultValue = "5") int maxResults) {

        if (incidentId == null || incidentId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required parameter: incidentId"));
        }

        UUID id;
        try {
            id = UUID.fromString(incidentId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid incidentId format. Must be a valid UUID."));
        }

        Optional<IncidentAnalysis> incident = knowledgeStoreRepository.findById(id);
        if (incident.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Incident not found: " + incidentId));
        }

        // Cap maxResults at 50
        maxResults = Math.min(Math.max(maxResults, 1), 50);

        List<ScoredMatch> similar = similarityMatchingService.findSimilar(incident.get(), maxResults);

        List<SimilarIncidentDto> dtos = similar.stream()
                .map(SimilarIncidentDto::from)
                .toList();

        return ResponseEntity.ok(Map.of(
                "incidentId", incidentId,
                "maxResults", maxResults,
                "matches", dtos
        ));
    }

    // --- DTOs ---

    record IncidentSummaryDto(
            String incidentId,
            String analysisDate,
            String service,
            String severity,
            String summary,
            int totalExceptions,
            int uniqueExceptionTypes,
            String status
    ) {
        static IncidentSummaryDto from(IncidentAnalysis a) {
            return new IncidentSummaryDto(
                    a.getIncidentId().toString(),
                    a.getAnalysisDate() != null ? a.getAnalysisDate().toString() : null,
                    a.getService(),
                    a.getSeverity(),
                    a.getSummary(),
                    a.getTotalExceptions(),
                    a.getUniqueExceptionTypes(),
                    a.getStatus()
            );
        }
    }

    record IncidentDetailDto(
            String incidentId,
            String analysisDate,
            String service,
            String sourceFilename,
            String timeRangeStart,
            String timeRangeEnd,
            int totalEvents,
            int totalExceptions,
            int uniqueExceptionTypes,
            Map<String, Integer> exceptionCounts,
            Set<String> exceptionTypes,
            Map<String, Double> errorDistribution,
            String rootCause,
            String impact,
            List<String> recommendations,
            String severity,
            String confidence,
            int confidencePercent,
            String summary,
            String status,
            String errorMessage
    ) {
        static IncidentDetailDto from(IncidentAnalysis a) {
            return new IncidentDetailDto(
                    a.getIncidentId().toString(),
                    a.getAnalysisDate() != null ? a.getAnalysisDate().toString() : null,
                    a.getService(),
                    a.getSourceFilename(),
                    a.getTimeRangeStart() != null ? a.getTimeRangeStart().toString() : null,
                    a.getTimeRangeEnd() != null ? a.getTimeRangeEnd().toString() : null,
                    a.getTotalEvents(),
                    a.getTotalExceptions(),
                    a.getUniqueExceptionTypes(),
                    a.getExceptionCounts(),
                    a.getExceptionTypes(),
                    a.getErrorDistribution(),
                    a.getRootCause(),
                    a.getImpact(),
                    a.getRecommendations(),
                    a.getSeverity(),
                    a.getConfidence(),
                    a.getConfidencePercent(),
                    a.getSummary(),
                    a.getStatus(),
                    a.getErrorMessage()
            );
        }
    }

    record SimilarIncidentDto(
            String incidentId,
            String service,
            String analysisDate,
            double similarityScore,
            String rootCause,
            Map<String, String> matchReasons
    ) {
        static SimilarIncidentDto from(ScoredMatch m) {
            return new SimilarIncidentDto(
                    m.getIncidentId().toString(),
                    m.getService(),
                    m.getAnalysisDate() != null ? m.getAnalysisDate().toString() : null,
                    m.getSimilarityScore(),
                    m.getRootCause(),
                    m.getMatchReasons()
            );
        }
    }
}
