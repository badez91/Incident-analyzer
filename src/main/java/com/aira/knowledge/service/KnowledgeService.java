package com.aira.knowledge.service;

import com.aira.knowledge.entity.EngineeringDocumentEntity;
import com.aira.knowledge.repository.EngineeringDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final EngineeringDocumentRepository repository;

    public KnowledgeService(EngineeringDocumentRepository repository) {
        this.repository = repository;
    }

    /**
     * Store a new engineering document in the knowledge base.
     */
    public EngineeringDocumentEntity storeDocument(EngineeringDocumentEntity doc) {
        log.info("Storing document: sourceType={}, referenceId={}, service={}",
                doc.getSourceType(), doc.getReferenceId(), doc.getServiceName());
        return repository.save(doc);
    }

    /**
     * Search documents by service name and/or exception type (for /search endpoint).
     */
    public List<EngineeringDocumentEntity> searchByMetadata(String service, String exception, int limit) {
        log.debug("Searching by metadata: service={}, exception={}, limit={}", service, exception, limit);
        return repository.searchByMetadata(
                service != null ? service : "",
                exception != null ? exception : "",
                limit
        );
    }

    /**
     * Find a document by its external reference ID (e.g., Jira key).
     */
    public Optional<EngineeringDocumentEntity> findByReference(String referenceId) {
        log.debug("Looking up document by referenceId={}", referenceId);
        return repository.findByReferenceId(referenceId);
    }

    /**
     * CONTENT-BASED similarity search.
     *
     * Finds tickets that are actually about the SAME KIND of issue based on:
     * 1. Description/RCA content similarity (full-text search) — PRIMARY signal
     * 2. Same exception type — STRONG signal (same exception = likely same root cause)
     * 3. Keywords from the incident summary and error behaviors
     *
     * Does NOT match just because tickets are from the same service.
     * CM-5551 (report storage) and CM-5553 (eTR batch upload) should NOT match.
     *
     * @param summary       incident summary (used for text search)
     * @param exceptionType exception type to find same-exception tickets
     * @param components    component keywords for additional text matching
     * @param maxResults    max results to return
     * @param excludeRef    referenceId to exclude (the ticket being analyzed)
     */
    public List<EngineeringDocumentEntity> findSimilar(String summary, String exceptionType,
                                                        List<String> components, int maxResults,
                                                        String excludeRef) {
        String excludeRefSafe = excludeRef != null ? excludeRef : "";

        // Build content-based search query from the actual incident content
        String textQuery = buildContentQuery(summary, exceptionType, components);

        log.debug("Finding similar: textQuery='{}', exceptionType={}, excludeRef={}, maxResults={}",
                truncate(textQuery, 80), exceptionType, excludeRef, maxResults);

        // Use a map to deduplicate by document ID and keep highest-relevance version
        Map<UUID, EngineeringDocumentEntity> resultMap = new LinkedHashMap<>();

        // Signal 1: Full-text content similarity (PRIMARY)
        if (!textQuery.isBlank()) {
            try {
                List<EngineeringDocumentEntity> textResults = repository.findByTextSimilarity(
                        textQuery, excludeRefSafe, maxResults);
                for (EngineeringDocumentEntity doc : textResults) {
                    resultMap.putIfAbsent(doc.getId(), doc);
                }
                log.debug("Text similarity found {} results", textResults.size());
            } catch (Exception e) {
                log.debug("Full-text search unavailable: {}", e.getMessage());
            }
        }

        // Signal 2: Same exception type (STRONG)
        if (exceptionType != null && !exceptionType.isBlank()) {
            try {
                List<EngineeringDocumentEntity> exceptionResults = repository.findByExceptionType(
                        exceptionType, excludeRefSafe, maxResults);
                for (EngineeringDocumentEntity doc : exceptionResults) {
                    resultMap.putIfAbsent(doc.getId(), doc);
                }
                log.debug("Exception type match found {} results", exceptionResults.size());
            } catch (Exception e) {
                log.debug("Exception search failed: {}", e.getMessage());
            }
        }

        // Signal 3: Fallback — most recent documents excluding self
        // (catches newly ingested similar tickets when full-text index isn't available)
        if (resultMap.isEmpty()) {
            List<EngineeringDocumentEntity> recent = repository.findAll().stream()
                    .filter(doc -> excludeRefSafe.isEmpty() || !excludeRefSafe.equals(doc.getReferenceId()))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(maxResults)
                    .toList();
            for (EngineeringDocumentEntity doc : recent) {
                resultMap.putIfAbsent(doc.getId(), doc);
            }
            log.debug("Recency fallback found {} results (full-text unavailable)", recent.size());
        }

        List<EngineeringDocumentEntity> results = resultMap.values().stream()
                .limit(maxResults)
                .collect(Collectors.toList());

        log.debug("Total similar documents found: {} (excluded: {})", results.size(), excludeRef);
        return results;
    }

    /**
     * Backward-compatible hybrid search (used by KnowledgeController /similar endpoint).
     */
    public List<EngineeringDocumentEntity> hybridSearch(String service, String exception,
                                                        List<String> components, int maxResults) {
        // For the generic search endpoint, use service as part of the query
        return findSimilar(service, exception, components, maxResults, null);
    }

    /**
     * Builds a content-focused search query from the incident's actual content.
     * Uses the summary and key error terms — NOT the service name.
     *
     * Example: "eTR batch upload failed Customer not updated FileNotFoundException"
     * This ensures we find tickets about the same PROBLEM, not just same SERVICE.
     */
    private String buildContentQuery(String summary, String exceptionType, List<String> components) {
        StringBuilder sb = new StringBuilder();

        // Use the incident summary as the primary search content
        if (summary != null && !summary.isBlank()) {
            // Clean up summary: remove Jira formatting, ticket references
            String cleaned = summary
                    .replaceAll("\\[INC\\d+\\]", "")    // Remove INC references
                    .replaceAll("[\\[\\]{}*_~^]", " ")   // Remove markup chars
                    .replaceAll("\\s+", " ")              // Normalize whitespace
                    .trim();
            sb.append(cleaned);
        }

        // Add exception type as a search term
        if (exceptionType != null && !exceptionType.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(exceptionType);
        }

        // Add component keywords
        if (components != null) {
            for (String comp : components) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(comp);
            }
        }

        return sb.toString().trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
