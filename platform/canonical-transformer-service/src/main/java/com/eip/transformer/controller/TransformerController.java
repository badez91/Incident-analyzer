package com.eip.transformer.controller;

import com.eip.common.dto.IncidentSubmission;
import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.transformer.service.EventBusPublisher;
import com.eip.transformer.service.TransformationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transform")
public class TransformerController {

    private final TransformationService transformationService;
    private final EventBusPublisher eventBusPublisher;

    public TransformerController(TransformationService transformationService, EventBusPublisher eventBusPublisher) {
        this.transformationService = transformationService;
        this.eventBusPublisher = eventBusPublisher;
    }

    @PostMapping
    public ResponseEntity<CanonicalIncidentEvent> transform(@Valid @RequestBody IncidentSubmission submission) {
        CanonicalIncidentEvent event = transformationService.transform(submission);
        eventBusPublisher.publishIncidentCreated(event);
        return ResponseEntity.ok(event);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<CanonicalIncidentEvent>> transformBatch(@Valid @RequestBody List<IncidentSubmission> submissions) {
        List<CanonicalIncidentEvent> results = submissions.stream()
            .map(s -> {
                CanonicalIncidentEvent event = transformationService.transform(s);
                eventBusPublisher.publishIncidentCreated(event);
                return event;
            })
            .toList();
        return ResponseEntity.ok(results);
    }

    @PostMapping("/logs")
    public ResponseEntity<CanonicalIncidentEvent> transformRawLogs(@RequestBody String rawLogContent) {
        IncidentSubmission submission = new IncidentSubmission("UNKNOWN", rawLogContent, null, null, "FILE_LOG", null);
        CanonicalIncidentEvent event = transformationService.transform(submission);
        eventBusPublisher.publishIncidentCreated(event);
        return ResponseEntity.ok(event);
    }
}
