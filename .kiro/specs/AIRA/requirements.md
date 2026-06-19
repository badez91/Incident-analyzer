# Requirements Document

## Introduction

This document defines the requirements for AIRA (Automated Investigation and Response Algorithm) — a modular monolith Spring Boot platform that performs automated incident investigation, RAG-based engineering knowledge retrieval, and AI-powered Root Cause Analysis. AIRA is designed as an autonomous engineering investigation system that evolves into a self-improving engineering intelligence copilot.

AIRA is NOT just an RCA tool. It is an autonomous engineering investigation system with a roadmap toward bug prediction, architecture recommendations, smart reporting, and AI agent-based workflows.

## Glossary

- **AIRA**: Automated Investigation and Response Algorithm — the engineering intelligence platform.
- **Modular Monolith**: Single deployable Spring Boot application with strict domain boundaries enforced via package structure.
- **Incident**: A reported event representing a system failure or degradation, containing a service name, severity, summary, and metadata.
- **Knowledge Store**: The PostgreSQL + pgvector-backed persistence layer storing engineering documents, incident history, RCA documents, and embeddings.
- **EngineeringDocument**: The canonical document model stored in the knowledge base, representing processed content from any source.
- **RCA**: Root Cause Analysis — the AI-generated determination of why an incident occurred.
- **RAG**: Retrieval-Augmented Generation — retrieve relevant context from knowledge store before sending to LLM.
- **Hybrid Search**: Retrieval combining metadata filtering (SQL), vector similarity (pgvector), and keyword search.
- **Document Intelligence**: The pipeline that parses raw content (Jira/Confluence/attachments) into structured EngineeringDocuments. No AI logic here.
- **Intelligence Engine**: The AIRA core reasoning engine that generates RCA using only RAG-retrieved context.
- **Agent**: Future autonomous workflow unit that orchestrates investigation tasks using Knowledge, Intelligence, and Integration services.
- **Ollama**: Local LLM inference server (Qwen models for RCA, nomic-embed-text for embeddings).

## Requirements

### Requirement 1: Modular Monolith Architecture

**User Story:** As a platform architect, I want strict domain boundaries within a single deployable application, so that each domain can be independently extracted into a microservice in the future.

#### Acceptance Criteria

1. THE system SHALL be a single Spring Boot application with domain separation via packages: `incident/`, `knowledge/`, `intelligence/`, `integration/`, `document/`, `agent/`, `reporting/`, `common/`.
2. THE system SHALL enforce no shared repository access between domains — each domain owns its own data.
3. THE system SHALL use DTOs for cross-domain communication, never shared entities.
4. THE system SHALL communicate between domains only via service interfaces (not direct repository calls).
5. THE system SHALL have no cyclic dependencies between domains.
6. EACH domain SHALL be independently extractable into a microservice without code changes to other domains.

### Requirement 2: Incident Domain

**User Story:** As a platform user, I want to ingest, manage, and simulate incidents from any source, so that the platform can investigate system failures regardless of origin.

#### Acceptance Criteria

1. WHEN an incident is submitted via POST /api/incidents with a valid payload containing serviceName, THE system SHALL return HTTP 200 with the created incident including a UUID.
2. WHEN an incident submission is missing serviceName, THE system SHALL return HTTP 400 with a validation error message.
3. WHEN an incident is retrieved via GET /api/incidents/{id}, THE system SHALL return the incident entity or HTTP 404 if not found.
4. WHEN incidents are searched via GET /api/incidents with optional service and status filters, THE system SHALL return matching incidents.
5. THE system SHALL persist incidents with fields: id, serviceName, severity (default MEDIUM), status (default INGESTED), summary, source (default MANUAL), jiraKey, metadata, createdAt, updatedAt.
6. THE system SHALL enforce severity values: CRITICAL, HIGH, MEDIUM, LOW.
7. THE system SHALL enforce status values: INGESTED, ANALYZING, RESOLVED, CLOSED.
8. THE system SHALL support incident simulation via POST /api/simulations (future) for generating realistic test incidents.

### Requirement 3: Knowledge Domain (Core of AIRA)

**User Story:** As a platform operator, I want all engineering knowledge persisted with embeddings for hybrid search, so that AIRA's brain accumulates and retrieves relevant context efficiently.

#### Acceptance Criteria

1. THE system SHALL store engineering documents in PostgreSQL with the canonical model: id, sourceType (JIRA/CONFLUENCE/GIT/INCIDENT/MANUAL), referenceId, summary, serviceName, environment, exceptionType, components (JSONB), structuredMetadata (JSONB), searchableText, embeddingVector (pgvector 768-dim), createdAt, updatedAt.
2. WHEN POST /api/knowledge/documents is called, THE system SHALL store the document and return the saved entity.
3. WHEN GET /api/knowledge/search is called with optional service and exception parameters, THE system SHALL return matching documents ordered by recency, limited by the limit parameter (default 5).
4. WHEN GET /api/knowledge/similar is called with a referenceId, THE system SHALL find the referenced document and return similar documents, excluding itself.
5. THE system SHALL support hybrid search combining: metadata filtering (SQL), vector similarity (pgvector), and keyword matching.
6. THE system SHALL generate embeddings using Ollama's nomic-embed-text model and store in pgvector.
7. WHEN Ollama is unavailable for embedding generation, THE system SHALL continue without embeddings — metadata search remains functional.
8. THE system SHALL store incident history, RCA documents, and engineering knowledge in the same canonical model.
9. THE system SHALL maintain database indexes on: source_type, service_name, exception_type, reference_id, and vector embedding (IVFFlat).
10. THE system SHALL target <500ms retrieval time for hybrid search.

### Requirement 4: Document Intelligence Domain

**User Story:** As a platform user, I want raw content from Jira, Confluence, and attachments automatically parsed into structured knowledge documents, so that the knowledge base accumulates useful engineering context without manual effort.

#### Acceptance Criteria

1. WHEN a Jira ticket is processed, THE system SHALL extract the service name from the Jira key prefix (e.g., CM-5553 → "cm").
2. WHEN a Jira ticket is processed, THE system SHALL extract exception types from the text using pattern matching.
3. WHEN a Jira ticket is processed, THE system SHALL extract component keywords from the text.
4. WHEN a Jira ticket is processed, THE system SHALL build searchable text from summary + description (max 1500 chars) + first comment (max 500 chars), capped at 2000 total.
5. WHEN a Jira ticket is processed, THE system SHALL create structured metadata JSON with jiraKey, priority, and labels.
6. WHEN a Jira ticket is processed, THE system SHALL build a compact summary including the title, detected exceptions, and error behaviors.
7. THE system SHALL store the processed document in the knowledge base via KnowledgeService.
8. THE Document Intelligence domain SHALL contain NO AI logic — only parsing, extraction, and normalization.
9. THE system SHALL support future parsing of: ADF format (Jira), Confluence wiki markup, table extraction, and attachment OCR/vision.

### Requirement 5: Intelligence Domain (AIRA Core Reasoning Engine)

**User Story:** As a platform user, I want AI-powered root cause analysis that receives ONLY RAG-retrieved context (never raw Jira data), so that investigations are fast, token-efficient, and enriched with historical knowledge.

#### Acceptance Criteria

1. WHEN POST /api/analyze is called with serviceName and summary, THE system SHALL execute the RCA pipeline: retrieve context → build prompt → call Ollama → parse response.
2. WHEN POST /api/analyze/jira/{key} is called, THE system SHALL fetch the Jira ticket, process it via Document Intelligence into the knowledge base, then run RCA using ONLY knowledge store context.
3. THE LLM SHALL NEVER receive raw Jira data, full comments, or attachment contents. Only RAG-retrieved context.
4. THE system SHALL retrieve up to 5 similar incidents + up to 3 RCA documents from the knowledge base before building the LLM prompt.
5. THE system SHALL build a compact prompt targeting <2000 tokens per request with high signal, low noise.
6. THE system SHALL call Ollama with the prompt and parse the response as structured JSON (rootCause, confidence, severity, businessImpact, recommendations, summary).
7. WHEN Ollama returns non-JSON or malformed response, THE system SHALL fall back to free-text extraction with confidence set to 30%.
8. WHEN Ollama is unreachable or returns null, THE system SHALL return a fallback result rather than failing.
9. THE system SHALL return an RcaResult containing: incidentId, severity, rootCause, confidencePercent, businessImpact, recommendations (immediate/shortTerm/longTerm), summary, contextUsed, tokensUsed, inferenceTimeMs, analyzedAt.
10. THE system SHALL achieve >90% reduction in LLM token usage compared to sending raw data.
11. THE system SHALL achieve <10 seconds RCA response time.
12. THE Intelligence domain SHALL support future capabilities: bug prediction, architecture recommendations, risk scoring.

### Requirement 6: Integration Domain (System Connectors)

**User Story:** As a platform user, I want connectors to external systems (Jira, Confluence, Git), so that AIRA can automatically ingest and correlate engineering data from multiple sources.

#### Acceptance Criteria

1. WHEN GET /api/jira/{key} is called with a valid Jira key, THE system SHALL fetch the ticket from Jira and return its details (key, summary, description, priority, status, assignee, labels, comments, created, updated).
2. WHEN the Jira API token is not configured, THE system SHALL log a warning and disable Jira integration gracefully.
3. WHEN POST /api/jira/poll is called with a JQL query, THE system SHALL search Jira and return matching issues.
4. THE system SHALL authenticate to Jira using Basic Auth (username + API token).
5. THE system SHALL gracefully handle Jira API failures by logging errors and returning empty results.
6. THE Integration domain SHALL contain NO AI logic — only data fetching and transport.
7. THE system SHALL support future integrations: Confluence (wiki/runbook search), Git (source code context), Grafana (metrics correlation).

### Requirement 7: Agent Domain (Future)

**User Story:** As a platform architect, I want an agent-ready architecture, so that AIRA can evolve into an autonomous investigation system with specialized agents.

#### Acceptance Criteria

1. THE system SHALL provide package structure `agent/` ready for future agent implementations.
2. AGENTS SHALL NOT access databases directly — they must use Knowledge Service, Intelligence Service, and Integration Service.
3. THE system SHALL support future agents: Incident Agent, Release Agent, Architecture Agent, Reporting Agent.
4. EACH agent SHALL orchestrate investigation workflows by composing service calls.

### Requirement 8: Reporting Domain (Future)

**User Story:** As an engineering manager, I want smart reporting and KPIs, so that I can track engineering health and incident trends.

#### Acceptance Criteria

1. THE system SHALL provide package structure `reporting/` ready for future reporting implementations.
2. THE system SHALL support future metrics: MTTR analysis, incident trends, engineering KPIs, smart dashboards.
3. THE system SHALL support future capabilities: bug prediction dashboards, architecture health scoring, system reliability metrics.

### Requirement 9: Error Handling and Resilience

**User Story:** As a platform operator, I want robust error handling, so that failures are reported consistently and don't cascade.

#### Acceptance Criteria

1. WHEN a validation error occurs on a request body, THE system SHALL return HTTP 400 with structured error details (timestamp, status, error, message, details).
2. WHEN a resource is not found, THE system SHALL return HTTP 404 with a descriptive message.
3. WHEN an external service (Ollama, Jira) is unavailable, THE system SHALL return HTTP 503 with a descriptive message.
4. WHEN an unhandled exception occurs, THE system SHALL return HTTP 500 with a generic message (no stack trace exposed).
5. THE system SHALL log all errors with appropriate severity levels.
6. THE system SHALL never expose sensitive configuration (API tokens, passwords) in error responses.

### Requirement 10: Health and Observability

**User Story:** As a platform operator, I want health endpoints and proper logging, so that I can monitor AIRA's operational status.

#### Acceptance Criteria

1. THE system SHALL expose GET /actuator/health returning UP/DOWN status.
2. THE system SHALL expose GET /actuator/info with application metadata.
3. THE system SHALL log structured messages with appropriate levels (INFO for operations, DEBUG for details, ERROR for failures).
4. THE system SHALL run on port 8080 by default.

### Requirement 11: Performance

**User Story:** As a platform user, I want fast, efficient analysis, so that investigations don't block engineering workflows.

#### Acceptance Criteria

1. THE system SHALL achieve >90% reduction in LLM token usage compared to raw data approach.
2. THE system SHALL achieve <10 seconds RCA response time.
3. THE system SHALL achieve <500ms knowledge retrieval time.
4. THE system SHALL maintain clean modular boundaries with no cross-domain coupling.
5. THE system SHALL be future microservice extraction ready.
