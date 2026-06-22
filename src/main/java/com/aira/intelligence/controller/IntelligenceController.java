package com.aira.intelligence.controller;

import com.aira.common.dto.InvestigationResult;
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
    public ResponseEntity<InvestigationResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        UUID incidentId = request.incidentId() != null ? request.incidentId() : UUID.randomUUID();

        InvestigationResult result = rcaEngine.investigate(
                incidentId,
                request.serviceName(),
                request.summary(),
                request.exceptionType(),
                request.components(),
                null, null, null
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/jira/{key}")
    public ResponseEntity<InvestigationResult> analyzeFromJira(@PathVariable String key) {
        log.info("Investigating Jira ticket: {}", key);

        // Step 1: Fetch the ticket
        JiraIssueDto issue = jiraConnector.getIssue(key)
                .orElseThrow(() -> new ResourceNotFoundException("Jira issue", key));

        // Step 2: Process via Document Intelligence → stores in Knowledge
        EngineeringDocumentEntity doc = documentIntelligenceService.processJiraTicket(
                issue.key(),
                issue.summary(),
                issue.description(),
                issue.priority(),
                issue.comments(),
                issue.labels(),
                issue.attachments()
        );

        // Step 3: Extract keywords from the ticket for similarity search
        String fullText = (issue.summary() != null ? issue.summary() : "") + " " +
                (issue.description() != null ? issue.description() : "");
        List<String> keywords = contentExtractor.extractKeywords(fullText);
        List<String> exceptions = contentExtractor.extractExceptionTypes(fullText);
        String exceptionType = exceptions.isEmpty() ? null : exceptions.get(0);
        List<String> components = contentExtractor.extractComponents(fullText);

        // Step 4: Search Jira for similar RESOLVED tickets (same type, keyword match)
        List<JiraIssueDto> similarTickets = jiraConnector.findSimilarResolved(
                issue.key(),
                issue.issueType(),
                keywords,
                2
        );

        log.info("Found {} similar resolved tickets for {} (type: {})",
                similarTickets.size(), key, issue.issueType());

        // Step 5: Process similar tickets in parallel (their RCA comments become context)
        similarTickets.parallelStream().forEach(similar ->
                documentIntelligenceService.processJiraTicket(
                        similar.key(),
                        similar.summary(),
                        similar.description(),
                        similar.priority(),
                        similar.comments(),
                        similar.labels(),
                        null  // skip attachments for similar tickets — save time
                )
        );

        // Step 6: Run RCA with full context (comments for RCA detection + source code lookup)
        String serviceName = contentExtractor.extractServiceName(issue.key());
        // Extend fullText with comments for source code reference extraction
        String allTicketText = fullText;
        if (issue.comments() != null && !issue.comments().isEmpty()) {
            allTicketText += "\n" + String.join("\n", issue.comments());
        }

        UUID incidentId = UUID.randomUUID();
        InvestigationResult result = rcaEngine.investigate(incidentId, serviceName, issue.summary(),
                exceptionType, components, issue.key(), allTicketText, issue.comments());

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
