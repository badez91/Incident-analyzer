package com.aira.knowledge.controller;

import com.aira.knowledge.entity.EngineeringDocumentEntity;
import com.aira.knowledge.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * Search documents by service name and/or exception type.
     */
    @GetMapping("/search")
    public ResponseEntity<List<EngineeringDocumentEntity>> search(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String exception,
            @RequestParam(defaultValue = "5") int limit) {

        List<EngineeringDocumentEntity> results = knowledgeService.searchByMetadata(service, exception, limit);
        return ResponseEntity.ok(results);
    }

    /**
     * Store a new engineering document.
     */
    @PostMapping("/documents")
    public ResponseEntity<EngineeringDocumentEntity> storeDocument(
            @RequestBody EngineeringDocumentEntity document) {

        EngineeringDocumentEntity saved = knowledgeService.storeDocument(document);
        return ResponseEntity.ok(saved);
    }

    /**
     * Find similar documents by reference ID — looks up the document first,
     * then finds similar ones based on its metadata.
     */
    @GetMapping("/similar")
    public ResponseEntity<List<EngineeringDocumentEntity>> findSimilar(
            @RequestParam String referenceId) {

        return knowledgeService.findByReference(referenceId)
                .map(doc -> {
                    List<EngineeringDocumentEntity> similar = knowledgeService.searchByMetadata(
                            doc.getServiceName(), doc.getExceptionType(), 5);
                    // Exclude the document itself from results
                    similar.removeIf(d -> d.getId().equals(doc.getId()));
                    return ResponseEntity.ok(similar);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
