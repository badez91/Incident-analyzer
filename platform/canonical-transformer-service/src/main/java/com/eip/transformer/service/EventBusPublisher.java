package com.eip.transformer.service;

import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EventBusPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventBusPublisher.class);
    private static final String STREAM_KEY = "incidents";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private boolean redisAvailable = true;

    public EventBusPublisher(@Autowired(required = false) StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        if (redisTemplate == null) {
            this.redisAvailable = false;
            log.warn("Redis not available — event publishing disabled, falling back to synchronous mode");
        }
    }

    public boolean publishIncidentCreated(CanonicalIncidentEvent event) {
        if (!redisAvailable || redisTemplate == null) {
            log.warn("Redis unavailable, skipping event publish for incident {}", event.incidentId());
            return false;
        }

        try {
            Map<String, Object> envelopePayload = Map.of(
                "incidentId", event.incidentId().toString(),
                "serviceName", event.serviceName(),
                "severity", event.severity()
            );

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "INCIDENT_CREATED", Instant.now(), "canonical-transformer", envelopePayload
            );

            String payload = objectMapper.writeValueAsString(event);
            String envelopeJson = objectMapper.writeValueAsString(envelope);

            RecordId recordId = redisTemplate.opsForStream().add(
                StreamRecords.string(Map.of("envelope", envelopeJson, "payload", payload)).withStreamKey(STREAM_KEY)
            );

            log.info("Published INCIDENT_CREATED event for incident {} (stream record: {})", event.incidentId(), recordId);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish event for incident {}: {}", event.incidentId(), e.getMessage());
            redisAvailable = false;
            return false;
        }
    }

    public boolean isRedisAvailable() {
        return redisAvailable;
    }
}
