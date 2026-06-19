CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- Knowledge Domain: Core RAG document store
CREATE TABLE engineering_document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type         VARCHAR(20) NOT NULL,
    reference_id        VARCHAR(200),
    summary             VARCHAR(500),
    service_name        VARCHAR(255),
    environment         VARCHAR(50),
    exception_type      VARCHAR(255),
    components          JSONB DEFAULT '[]',
    structured_metadata JSONB DEFAULT '{}',
    searchable_text     TEXT,
    embedding_vector    vector(768),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_source ON engineering_document(source_type);
CREATE INDEX idx_doc_service ON engineering_document(service_name);
CREATE INDEX idx_doc_exception ON engineering_document(exception_type);
CREATE INDEX idx_doc_reference ON engineering_document(reference_id);

-- Incident Domain
CREATE TABLE incident (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name    VARCHAR(255) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20) NOT NULL DEFAULT 'INGESTED',
    summary         TEXT,
    source          VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    jira_key        VARCHAR(50),
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incident_service ON incident(service_name);
CREATE INDEX idx_incident_status ON incident(status);
CREATE INDEX idx_incident_jira ON incident(jira_key);

-- Intelligence Domain: RCA results
CREATE TABLE rca_result (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id         UUID NOT NULL REFERENCES incident(id),
    severity            VARCHAR(20),
    root_cause          TEXT,
    confidence_percent  INTEGER DEFAULT 0,
    business_impact     JSONB DEFAULT '[]',
    recommendations     JSONB DEFAULT '{}',
    summary             TEXT,
    context_used        JSONB DEFAULT '[]',
    tokens_used         INTEGER DEFAULT 0,
    inference_time_ms   BIGINT DEFAULT 0,
    analyzed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rca_incident ON rca_result(incident_id);

-- Constraints
ALTER TABLE incident ADD CONSTRAINT chk_incident_severity
    CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE incident ADD CONSTRAINT chk_incident_status
    CHECK (status IN ('INGESTED', 'ANALYZING', 'RESOLVED', 'CLOSED'));
ALTER TABLE engineering_document ADD CONSTRAINT chk_doc_source
    CHECK (source_type IN ('JIRA', 'CONFLUENCE', 'GIT', 'INCIDENT', 'MANUAL'));
