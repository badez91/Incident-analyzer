-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- INCIDENT TABLE
-- ============================================================
CREATE TABLE incident (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    severity        VARCHAR(20) NOT NULL,
    source          VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'INGESTED',
    symptoms        JSONB,
    metadata        JSONB,
    raw_payload     JSONB,
    exception_counts JSONB,
    exception_types TEXT[],
    error_distribution JSONB,
    total_events    INT DEFAULT 0,
    total_exceptions INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_incident_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_incident_source CHECK (source IN ('FILE_LOG', 'DOCKER_LOG', 'MANUAL', 'SIMULATOR', 'GRAFANA', 'API')),
    CONSTRAINT chk_incident_status CHECK (status IN ('INGESTED', 'TRANSFORMING', 'ANALYZING', 'COMPLETE', 'FAILED'))
);

-- ============================================================
-- INCIDENT ANALYSIS TABLE
-- ============================================================
CREATE TABLE incident_analysis (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id         UUID NOT NULL REFERENCES incident(id),
    category            VARCHAR(100),
    root_cause          TEXT,
    severity            VARCHAR(20),
    confidence          DOUBLE PRECISION,
    confidence_percent  INT,
    summary             TEXT,
    business_impact     JSONB,
    recommendations     JSONB,
    evidence            JSONB,
    llm_model           VARCHAR(100),
    llm_provider        VARCHAR(100),
    inference_time_ms   BIGINT,
    raw_response        TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'COMPLETE',
    error_message       TEXT,
    analyzed_at         TIMESTAMPTZ,

    CONSTRAINT chk_analysis_status CHECK (status IN ('COMPLETE', 'INCOMPLETE', 'FAILED'))
);

-- ============================================================
-- INCIDENT RESOLUTION TABLE
-- ============================================================
CREATE TABLE incident_resolution (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id         UUID NOT NULL REFERENCES incident(id),
    analysis_id         UUID REFERENCES incident_analysis(id),
    resolved_by         VARCHAR(255),
    resolution_summary  TEXT,
    resolution_steps    JSONB,
    verification_notes  TEXT,
    time_to_resolve_ms  BIGINT,
    resolved_at         TIMESTAMPTZ
);

-- ============================================================
-- INCIDENT SIMILARITY TABLE
-- ============================================================
CREATE TABLE incident_similarity (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_a_id   UUID NOT NULL REFERENCES incident(id),
    incident_b_id   UUID NOT NULL REFERENCES incident(id),
    similarity_score DOUBLE PRECISION NOT NULL,
    match_reasons   JSONB,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_similarity_pair UNIQUE (incident_a_id, incident_b_id)
);

-- ============================================================
-- KNOWLEDGE REFERENCE TABLE
-- ============================================================
CREATE TABLE knowledge_reference (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id     UUID REFERENCES incident(id),
    analysis_id     UUID REFERENCES incident_analysis(id),
    reference_type  VARCHAR(50),
    external_id     VARCHAR(255),
    external_url    TEXT,
    title           VARCHAR(500),
    content_snippet TEXT,
    relevance_score DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- CANONICAL EVENT TABLE
-- ============================================================
CREATE TABLE canonical_event (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID NOT NULL REFERENCES incident(id) ON DELETE CASCADE,
    timestamp   TIMESTAMPTZ,
    level       VARCHAR(10),
    service     VARCHAR(255),
    event_type  VARCHAR(50),
    message     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_event_level CHECK (level IN ('ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE')),
    CONSTRAINT chk_event_type CHECK (event_type IN ('EXCEPTION', 'ERROR', 'WARNING', 'STACK_TRACE', 'INFO'))
);

-- ============================================================
-- INDEXES
-- ============================================================

-- Incident indexes
CREATE INDEX idx_incident_service ON incident(service_name);
CREATE INDEX idx_incident_status ON incident(status);
CREATE INDEX idx_incident_created ON incident(created_at DESC);
CREATE INDEX idx_incident_types ON incident USING GIN(exception_types);

-- Analysis indexes
CREATE INDEX idx_analysis_incident ON incident_analysis(incident_id);
CREATE INDEX idx_analysis_category ON incident_analysis(category);

-- Resolution indexes
CREATE INDEX idx_resolution_incident ON incident_resolution(incident_id);

-- Similarity indexes
CREATE INDEX idx_similarity_a ON incident_similarity(incident_a_id);
CREATE INDEX idx_similarity_b ON incident_similarity(incident_b_id);

-- Knowledge reference indexes
CREATE INDEX idx_reference_incident ON knowledge_reference(incident_id);
CREATE INDEX idx_reference_type ON knowledge_reference(reference_type);

-- Canonical event indexes
CREATE INDEX idx_event_incident ON canonical_event(incident_id);
CREATE INDEX idx_event_type ON canonical_event(event_type);
CREATE INDEX idx_event_level ON canonical_event(level);
