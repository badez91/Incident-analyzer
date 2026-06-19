package com.aira.document.service;

import com.aira.integration.jira.JiraAttachmentDto;
import com.aira.knowledge.entity.EngineeringDocumentEntity;
import com.aira.knowledge.service.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final KnowledgeService knowledgeService;
    private final ContentExtractor contentExtractor;
    private final AttachmentProcessor attachmentProcessor;

    public DocumentIntelligenceService(KnowledgeService knowledgeService,
                                       ContentExtractor contentExtractor,
                                       AttachmentProcessor attachmentProcessor) {
        this.knowledgeService = knowledgeService;
        this.contentExtractor = contentExtractor;
        this.attachmentProcessor = attachmentProcessor;
    }

    /**
     * Full Jira ticket processing pipeline:
     * 1. Extract metadata from text (service, exceptions, components)
     * 2. Process attachments (OCR images, read text files)
     * 3. Combine all extracted content into searchable text
     * 4. Store as EngineeringDocument in Knowledge Store
     */
    public EngineeringDocumentEntity processJiraTicket(String jiraKey, String summary, String description,
                                                       String priority, List<String> comments,
                                                       List<String> labels, List<JiraAttachmentDto> attachments) {
        log.info("Processing Jira ticket: {} (attachments: {})", jiraKey,
                attachments != null ? attachments.size() : 0);

        // Extract service name from Jira key prefix
        String serviceName = contentExtractor.extractServiceName(jiraKey);

        // Combine text for analysis
        String fullText = (summary != null ? summary : "") + " " + (description != null ? description : "");

        // Process attachments (OCR for images, read text files)
        List<AttachmentProcessor.AttachmentContent> attachmentContents = List.of();
        if (attachments != null && !attachments.isEmpty()) {
            attachmentContents = attachmentProcessor.processAttachments(attachments);
        }

        // Combine attachment text with ticket text for better extraction
        String attachmentText = attachmentContents.stream()
                .filter(a -> !"metadata".equals(a.extractionMethod()))
                .map(AttachmentProcessor.AttachmentContent::extractedText)
                .collect(Collectors.joining("\n"));

        String combinedText = fullText + (attachmentText.isBlank() ? "" : "\n" + attachmentText);

        // Extract exception types from combined text (includes OCR'd content)
        List<String> exceptionTypes = contentExtractor.extractExceptionTypes(combinedText);
        String exceptionType = exceptionTypes.isEmpty() ? null : exceptionTypes.get(0);

        // Extract components from labels and combined text
        List<String> extractedComponents = contentExtractor.extractComponents(combinedText);
        List<String> components = new java.util.ArrayList<>(extractedComponents);
        if (labels != null) {
            labels.stream()
                    .map(String::toLowerCase)
                    .filter(l -> !components.contains(l))
                    .forEach(components::add);
        }

        // Build searchable text (now includes attachment content)
        String searchableText = buildEnrichedSearchableText(summary, description, comments, attachmentContents);

        // Build structured metadata (includes attachment info)
        String structuredMetadata = buildStructuredMetadata(jiraKey, priority, labels, attachmentContents);

        // Build document summary
        List<String> behaviors = contentExtractor.extractErrorBehaviors(combinedText);
        String docSummary = contentExtractor.buildSummary(summary, exceptionTypes, behaviors);

        // Create and store document
        EngineeringDocumentEntity doc = new EngineeringDocumentEntity();
        doc.setSourceType("JIRA");
        doc.setReferenceId(jiraKey);
        doc.setServiceName(serviceName);
        doc.setExceptionType(exceptionType);
        doc.setComponents(components.isEmpty() ? null : toJsonArray(components));
        doc.setSearchableText(searchableText);
        doc.setStructuredMetadata(structuredMetadata);
        doc.setSummary(docSummary);

        EngineeringDocumentEntity stored = knowledgeService.storeDocument(doc);
        log.info("Stored Jira ticket {} as document id={} (with {} attachment extractions)",
                jiraKey, stored.getId(), attachmentContents.size());
        return stored;
    }

    /**
     * Backward-compatible overload (without attachments).
     */
    public EngineeringDocumentEntity processJiraTicket(String jiraKey, String summary, String description,
                                                       String priority, List<String> comments,
                                                       List<String> labels) {
        return processJiraTicket(jiraKey, summary, description, priority, comments, labels, null);
    }

    /**
     * Builds enriched searchable text that includes attachment content.
     * Priority: summary > description > OCR/text extraction > comments
     */
    private String buildEnrichedSearchableText(String summary, String description,
                                               List<String> comments,
                                               List<AttachmentProcessor.AttachmentContent> attachments) {
        StringBuilder sb = new StringBuilder();

        // Summary (full)
        if (summary != null) {
            sb.append(summary).append(" ");
        }

        // Description (max 1200 chars to leave room for attachments)
        if (description != null) {
            sb.append(description, 0, Math.min(description.length(), 1200)).append(" ");
        }

        // Attachment extracted content (max 500 chars total — high-value OCR content)
        if (attachments != null && !attachments.isEmpty()) {
            int attachBudget = 500;
            for (AttachmentProcessor.AttachmentContent att : attachments) {
                if ("metadata".equals(att.extractionMethod())) continue;
                String text = att.extractedText();
                if (text.length() > attachBudget) {
                    text = text.substring(0, attachBudget);
                }
                sb.append("[").append(att.filename()).append("] ").append(text).append(" ");
                attachBudget -= text.length();
                if (attachBudget <= 0) break;
            }
        }

        // First comment (max 300 chars)
        if (comments != null && !comments.isEmpty()) {
            String firstComment = comments.get(0);
            sb.append(firstComment, 0, Math.min(firstComment.length(), 300));
        }

        String result = sb.toString().trim();
        return result.length() > 2500 ? result.substring(0, 2500) : result;
    }

    private String buildStructuredMetadata(String jiraKey, String priority, List<String> labels,
                                           List<AttachmentProcessor.AttachmentContent> attachments) {
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("jiraKey", jiraKey);
            if (priority != null) meta.put("priority", priority);
            if (labels != null) meta.put("labels", labels);

            // Include attachment metadata
            if (attachments != null && !attachments.isEmpty()) {
                List<Map<String, String>> attMeta = attachments.stream()
                        .map(a -> Map.of(
                                "filename", a.filename(),
                                "type", a.mimeType() != null ? a.mimeType() : "unknown",
                                "method", a.extractionMethod()
                        ))
                        .toList();
                meta.put("attachments", attMeta);
                meta.put("attachmentCount", attachments.size());
                meta.put("ocrExtracted", attachments.stream()
                        .anyMatch(a -> "ocr".equals(a.extractionMethod())));
            }

            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("Failed to build structured metadata: {}", e.getMessage());
            return "{}";
        }
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}
