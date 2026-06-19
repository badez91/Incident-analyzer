package com.aira.knowledge.service;

import com.aira.knowledge.entity.EngineeringDocumentEntity;
import com.aira.knowledge.repository.EngineeringDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
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
     * Search documents by service name and/or exception type with a configurable limit.
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
     * Find a document by its external reference ID (e.g., Jira key, log correlation ID).
     */
    public Optional<EngineeringDocumentEntity> findByReference(String referenceId) {
        log.debug("Looking up document by referenceId={}", referenceId);
        return repository.findByReferenceId(referenceId);
    }

    /**
     * Hybrid search combining metadata filters (service, exception, components).
     * Returns top results ranked by recency.
     * Future: will incorporate vector similarity when pgvector embeddings are active.
     */
    public List<EngineeringDocumentEntity> hybridSearch(String service, String exception,
                                                        List<String> components, int maxResults) {
        log.debug("Hybrid search: service={}, exception={}, components={}, maxResults={}",
                service, exception, components, maxResults);

        // Start with metadata-based search
        List<EngineeringDocumentEntity> results = repository.searchByMetadata(
                service != null ? service : "",
                exception != null ? exception : "",
                maxResults * 2  // fetch more, then filter
        );

        // If components filter is provided, boost/filter results containing those components
        if (components != null && !components.isEmpty()) {
            List<EngineeringDocumentEntity> filtered = results.stream()
                    .filter(doc -> {
                        if (doc.getComponents() == null) return false;
                        String comps = doc.getComponents().toLowerCase();
                        return components.stream()
                                .anyMatch(c -> comps.contains(c.toLowerCase()));
                    })
                    .collect(Collectors.toList());

            // If we have enough component-matched results, use them; otherwise fall back to all
            if (!filtered.isEmpty()) {
                results = filtered;
            }
        }

        // Limit to maxResults
        return results.stream()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
}
