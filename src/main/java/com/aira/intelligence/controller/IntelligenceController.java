package com.aira.intelligence.controller;

import com.aira.common.dto.RcaResult;
import com.aira.common.exception.ResourceNotFoundException;
import com.aira.document.service.ContentExtractor;
import com.aira.document.service.DocumentIntelligenceService;
import com.aira.integration.jira.JiraConnector;
import com.aira.integration.jira.JiraIssueDto;
import com.aira.intelligence.service.RcaEngine;
import com.aira.knowledge.entity.EngineeringDocumentEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analyze")
public class IntelligenceController {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceController.class);

    private final RcaEngine rcaEngine;
    private final JiraConnector jiraConnector;
    private final DocumentIntelligenceService documentIntelligenceService;
    private final ContentExtractor contentExtractor;

    public IntelligenceController(RcaEngine rcaEngine,
                                   JiraConnector jiraConnector,
                                   DocumentIntelligenceService documentIntelligenceService,
                                   ContentExtractor contentExtractor) {
        this.rcaEngine = rcaEngine;
        this.jiraConnector = jiraConnector;
        this.documentIntelligenceService = documentIntelligenceService;
        this.contentExtractor = contentExtractor;
    }

    @PostMapping
    public ResponseEntity<RcaResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        UUID incidentId = request.incidentId() != null ? request.incidentId() : UUID.randomUUID();

        RcaResult result = rcaEngine.analyze(
                incidentId,
                request.serviceName(),
                request.summary(),
                request.exceptionType(),
                request.components()
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/jira/{key}")
    public ResponseEntity<RcaResult> analyzeFromJira(@PathVariable String key) {
        log.info("Analyzing Jira ticket: {}", key);

        JiraIssueDto issue = jiraConnector.getIssue(key)
                .orElseThrow(() -> new ResourceNotFoundException("Jira issue", key));

        // Process via DocumentIntelligenceService (includes attachment OCR) → stores in Knowledge
        EngineeringDocumentEntity doc = documentIntelligenceService.processJiraTicket(
                issue.key(),
                issue.summary(),
                issue.description(),
                issue.priority(),
                issue.comments(),
                issue.labels(),
                issue.attachments()
        );

        // Extract analysis parameters from combined content
        String serviceName = contentExtractor.extractServiceName(issue.key());
        String fullText = (issue.summary() != null ? issue.summary() : "") + " " +
                (issue.description() != null ? issue.description() : "");
        List<String> exceptions = contentExtractor.extractExceptionTypes(fullText);
        String exceptionType = exceptions.isEmpty() ? null : exceptions.get(0);
        List<String> components = contentExtractor.extractComponents(fullText);

        // Run RCA
        UUID incidentId = UUID.randomUUID();
        RcaResult result = rcaEngine.analyze(incidentId, serviceName, issue.summary(), exceptionType, components);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<String> getStoredRca(@PathVariable UUID incidentId) {
        return ResponseEntity.ok("{\"message\": \"Stored RCA retrieval not yet implemented\", \"incidentId\": \"" + incidentId + "\"}");
    }

    public record AnalyzeRequest(
            UUID incidentId,
            @NotBlank(message = "serviceName is required")
            String serviceName,
            @NotBlank(message = "summary is required")
            String summary,
            String exceptionType,
            List<String> components
    ) {}
}
