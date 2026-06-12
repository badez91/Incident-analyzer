-- Incident Knowledge Platform Schema
-- V1: Create core tables for incident analyses and canonical events

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Main table: stores structured incident analysis records
CREATE TABLE incident_analyses (
    incident_id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    analysis_date          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    service                VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN',
    source_filename        VARCHAR(500),
    time_range_start       TIMESTAMP WITH TIME ZONE,
    time_range_end         TIMESTAMP WITH TIME ZONE,
    total_events           INTEGER NOT NULL DEFAULT 0,
    total_exceptions       INTEGER NOT NULL DEFAULT 0,
    unique_exception_types INTEGER NOT NULL DEFAULT 0,
    exception_counts       JSONB NOT NULL DEFAULT '{}',
    exception_types        TEXT[] NOT NULL DEFAULT '{}',
    error_distribution     JSONB NOT NULL DEFAULT '{}',
    root_cause             TEXT,
    impact                 TEXT,
    recommendations        JSONB DEFAULT '[]',
    llm_analysis           JSONB,
    severity               VARCHAR(20),
    confidence             VARCHAR(20),
    confidence_percent     INTEGER DEFAULT 0,
    summary                TEXT,
    raw_response           TEXT,
    status                 VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
    error_message          TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for incident_analyses
CREATE INDEX idx_analyses_service ON incident_analyses(service);
CREATE INDEX idx_analyses_date ON incident_analyses(analysis_date DESC);
CREATE INDEX idx_analyses_exception_types ON incident_analyses USING GIN(exception_types);
CREATE INDEX idx_analyses_status ON incident_analyses(status);

-- Canonical events table: normalized log lines linked to incidents
CREATE TABLE canonical_events (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id  UUID NOT NULL REFERENCES incident_analyses(incident_id) ON DELETE CASCADE,
    timestamp    TIMESTAMP WITH TIME ZONE,
    level        VARCHAR(10) NOT NULL,
    service      VARCHAR(255),
    event_type   VARCHAR(50) NOT NULL,
    message      TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for canonical_events
CREATE INDEX idx_events_incident ON canonical_events(incident_id);
CREATE INDEX idx_events_type ON canonical_events(event_type);
CREATE INDEX idx_events_level ON canonical_events(level);
CREATE INDEX idx_events_timestamp ON canonical_events(timestamp);

-- Add check constraints for data integrity
ALTER TABLE canonical_events
    ADD CONSTRAINT chk_event_level CHECK (level IN ('ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'));

ALTER TABLE canonical_events
    ADD CONSTRAINT chk_event_type CHECK (event_type IN ('EXCEPTION', 'ERROR', 'WARNING', 'STACK_TRACE', 'INFO'));

ALTER TABLE incident_analyses
    ADD CONSTRAINT chk_analysis_status CHECK (status IN ('COMPLETE', 'INCOMPLETE', 'FAILED'));
