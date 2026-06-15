package com.eip.ingestion.controller;

import com.eip.common.dto.IncidentSubmission;
import com.eip.common.dto.IngestionResponse;
import com.eip.ingestion.service.IngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<IngestionResponse> ingest(@Valid @RequestBody IncidentSubmission submission) {
        // Additional validation beyond @NotBlank
        validateSubmission(submission);
        IngestionResponse response = ingestionService.ingest(submission);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<IngestionResponse>> ingestBatch(@RequestBody List<IncidentSubmission> submissions) {
        List<IngestionResponse> responses = new ArrayList<>();
        for (IncidentSubmission submission : submissions) {
            try {
                validateSubmission(submission);
                responses.add(ingestionService.ingest(submission));
            } catch (IllegalArgumentException e) {
                responses.add(new IngestionResponse(
                        null, "REJECTED", java.time.Instant.now(), e.getMessage()));
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responses);
    }

    private void validateSubmission(IncidentSubmission submission) {
        if (submission.serviceName() == null || submission.serviceName().isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        if (submission.serviceName().length() > 255) {
            throw new IllegalArgumentException("serviceName must not exceed 255 characters");
        }
        if (submission.description() == null || submission.description().isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (submission.description().length() > 10000) {
            throw new IllegalArgumentException("description must not exceed 10000 characters");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidation(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
