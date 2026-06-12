package com.loganalyzer.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * A normalized representation of a single log line with consistent schema.
 */
public class CanonicalEvent {

    private UUID id;
    private UUID incidentId;

    @NotNull(message = "Timestamp must not be null")
    private Instant timestamp;

    @NotNull(message = "Level must not be null")
    @Pattern(regexp = "ERROR|WARN|INFO|DEBUG|TRACE", message = "Level must be one of: ERROR, WARN, INFO, DEBUG, TRACE")
    private String level;

    private String service;

    @NotNull(message = "EventType must not be null")
    @Pattern(regexp = "EXCEPTION|ERROR|WARNING|STACK_TRACE|INFO", message = "EventType must be one of: EXCEPTION, ERROR, WARNING, STACK_TRACE, INFO")
    private String eventType;

    @NotNull(message = "Message must not be null")
    @Size(min = 1, max = 4000, message = "Message must be between 1 and 4000 characters")
    private String message;

    public CanonicalEvent() {}

    public CanonicalEvent(UUID id, UUID incidentId, Instant timestamp, String level,
                          String service, String eventType, String message) {
        this.id = id;
        this.incidentId = incidentId;
        this.timestamp = timestamp;
        this.level = level;
        this.service = service;
        this.eventType = eventType;
        this.message = message;
    }

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
