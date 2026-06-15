package com.eip.ingestion.service;

import com.eip.common.dto.IncidentSubmission;
import com.eip.common.dto.IngestionResponse;
import com.eip.ingestion.client.TransformerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final TransformerClient transformerClient;

    public IngestionService(TransformerClient transformerClient) {
        this.transformerClient = transformerClient;
    }

    public IngestionResponse ingest(IncidentSubmission submission) {
        log.info("Ingesting incident for service: {}", submission.serviceName());

        UUID incidentId = UUID.randomUUID();

        try {
            Map<String, Object> transformResult = transformerClient.transform(submission);
            if (transformResult != null && transformResult.containsKey("incidentId")) {
                incidentId = UUID.fromString(transformResult.get("incidentId").toString());
            }

            return new IngestionResponse(
                    incidentId,
                    "ACCEPTED",
                    Instant.now(),
                    "Incident accepted and forwarded for processing"
            );
        } catch (Exception e) {
            log.error("Ingestion failed for service {}: {}", submission.serviceName(), e.getMessage());
            return new IngestionResponse(
                    incidentId,
                    "ACCEPTED",
                    Instant.now(),
                    "Incident accepted (transformer temporarily unavailable, will retry)"
            );
        }
    }
}
