# Requirements Document

## Introduction

This document defines the requirements for evolving the existing AI Log Analyzer monolith into the Engineering Intelligence Platform — a microservice-based system that ingests incidents from multiple sources, normalizes them into canonical events, performs AI-powered Root Cause Analysis enriched with historical context, and accumulates engineering knowledge over time. The platform is source-agnostic, container-friendly (Docker Compose), and designed for future Kubernetes scaling and integrations with Jira, Confluence, and source code repositories.

## Glossary

- **Platform**: The Engineering Intelligence Platform, a collection of microservices that collectively ingest, analyze, and store engineering incident knowledge.
- **Incident**: A reported event representing a system failure or degradation, containing a service name, description, log snippets, and metadata.
- **CanonicalIncidentEvent**: The normalized representation of an incident after transformation, with consistent structure regardless of source.
- **CanonicalEvent**: A normalized representation of a single log line with fields: eventId, incidentId, timestamp, level, service, eventType, message.
- **IncidentAnalysisResult**: The output of AI-powered analysis containing category, root cause, severity, confidence, recommendations, and evidence.
- **ScoredMatch**: A historical incident paired with its computed similarity score and match reasoning.
- **Knowledge Store**: The PostgreSQL-backed persistence layer storing incidents, analyses, resolutions, similarity relationships, and knowledge references.
- **Event Bus**: Redis Streams-based asynchronous communication layer between microservices.
- **Failure Simulator**: A service that generates realistic incident data for testing and demonstration without production dependencies.
- **API Gateway**: The single entry point for all external requests, routing to appropriate microservices.
- **RCA**: Root Cause Analysis — the AI-generated determination of why an incident occurred.

## Requirements

### Requirement 1: Incident Ingestion

**User Story:** As a platform user, I want to submit incidents from any source through a unified API, so that the platform can analyze failures regardless of where they originate.

#### Acceptance Criteria

1. WHEN an incident submission is made via POST /api/incidents with a valid payload containing serviceName and description, THE Incident Ingestion Service SHALL return HTTP 202 Accepted with an incidentId (UUID) and status ACCEPTED.
2. WHEN an incident submission is missing serviceName or description, THE Incident Ingestion Service SHALL return HTTP 400 Bad Request with a validation error message and status REJECTED.
3. WHEN an incident submission includes logSnippets, THE Incident Ingestion Service SHALL forward all log snippets to the Canonical Transformer along with the description.
4. WHEN an incident submission does not specify a source, THE Incident Ingestion Service SHALL default the source to MANUAL.
5. THE Incident Ingestion Service SHALL accept source values from the set: FILE_LOG, DOCKER_LOG, MANUAL, SIMULATOR, GRAFANA, API.
6. WHEN a batch of incident submissions is made via POST /api/incidents/batch, THE Incident Ingestion Service SHALL process each submission independently and return individual acceptance/rejection statuses per incident.
7. THE Incident Ingestion Service SHALL validate that serviceName does not exceed 255 characters and description does not exceed 10000 characters.
8. THE Incident Ingestion Service SHALL expose a health endpoint at GET /health that returns HTTP 200 when the service is operational.

### Requirement 2: Canonical Event Transformation

**User Story:** As a platform operator, I want all incident formats normalized into a single canonical model, so that downstream services process incidents consistently regardless of source.

#### Acceptance Criteria

1. WHEN an IncidentSubmission is received, THE Canonical Transformer Service SHALL produce a CanonicalIncidentEvent containing: incidentId (UUID), serviceName, severity, symptoms, timestamp, source, rawPayload, events, exceptionCounts, exceptionTypes, errorDistribution, totalEvents, totalExceptions.
2. WHEN parsing log content, THE Canonical Transformer Service SHALL detect timestamps in formats: "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-ddTHH:mm:ss.SSS", "yyyy/MM/dd HH:mm:ss", "dd/MMM/yyyy:HH:mm:ss Z", and normalize them to ISO-8601 format. Lines without recognizable timestamps SHALL use the current processing time.
3. WHEN parsing log content, THE Canonical Transformer Service SHALL detect log levels (ERROR, WARN/WARNING→WARN, INFO, DEBUG, TRACE) case-insensitively. Lines without a recognizable level SHALL default to INFO.
4. WHEN parsing log content, THE Canonical Transformer Service SHALL classify each line into eventType: EXCEPTION (Java exception class present), ERROR (level ERROR without exception), WARNING (level WARN), STACK_TRACE (line starts with whitespace + "at "), or INFO (default).
5. WHEN exceptions are detected, THE Canonical Transformer Service SHALL produce an exceptionCounts map with each exception type and its occurrence count, and an errorDistribution map where percentages sum to exactly 100.0.
6. WHEN the total number of exceptions exceeds 100 OR OutOfMemoryError/StackOverflowError/ThreadDeath is detected, THE severity SHALL be CRITICAL. WHEN exceptions exceed 50 OR unique types exceed 5, severity SHALL be HIGH. WHEN exceptions exceed 10, severity SHALL be MEDIUM. Otherwise severity SHALL be LOW.
7. WHEN transformation completes, THE Canonical Transformer Service SHALL publish an INCIDENT_CREATED event to the Event Bus (Redis Streams) containing the full CanonicalIncidentEvent.
8. THE Canonical Transformer Service SHALL preserve the ordering of events as they appear in the input log content.
9. THE Canonical Transformer Service SHALL truncate event messages to a maximum of 4000 bytes at a valid UTF-8 character boundary.
10. THE Canonical Transformer Service SHALL extract service names from fully-qualified class names or bracketed identifiers in log lines. Lines without identifiable service names SHALL have service set to "UNKNOWN".

### Requirement 3: Incident Analysis and RCA Generation

**User Story:** As a platform user, I want AI-powered root cause analysis enriched with historical context, so that incidents are diagnosed faster and more accurately over time.

#### Acceptance Criteria

1. WHEN an INCIDENT_CREATED event is consumed from the Event Bus, THE Incident Analysis Service SHALL execute the analysis pipeline: store incident → find similar → build enhanced prompt → call AI → persist result → publish ANALYSIS_COMPLETE.
2. WHEN similar historical incidents exist (score >= 0.3), THE Incident Analysis Service SHALL include up to 5 similar incidents in the LLM prompt, ordered by similarity score descending. Each entry SHALL include the incident's service, root cause, similarity percentage, and match factors.
3. WHEN no similar incidents exist OR all similar incidents have null rootCause, THE Incident Analysis Service SHALL send the base prompt without historical context.
4. THE Incident Analysis Service SHALL cap the historical context section of the enhanced prompt at 8000 characters, including complete entries in score-descending order and omitting entries that would exceed the cap.
5. WHEN the AI service returns a valid JSON response, THE Incident Analysis Service SHALL produce an IncidentAnalysisResult with status COMPLETE containing: category, rootCause, severity, confidence, confidencePercent, summary, businessImpact, recommendations (immediate/shortTerm/longTerm), and evidence.
6. WHEN the AI service returns an unparseable response, THE Incident Analysis Service SHALL store the raw response (truncated to 32000 characters), mark status INCOMPLETE, and set structured=false.
7. WHEN the AI service is unreachable after 3 retry attempts (exponential backoff: 1s, 2s, 4s), THE Incident Analysis Service SHALL mark the analysis as FAILED with an error message describing the failure.
8. THE Incident Analysis Service SHALL persist the completed analysis to the Knowledge Store BEFORE publishing the ANALYSIS_COMPLETE event.
9. THE Incident Analysis Service SHALL support a reanalyze endpoint (POST /api/analysis/{incidentId}/reanalyze) that allows re-running the analysis pipeline for a previously analyzed incident with a different model or updated parameters.
10. WHEN the Similarity Service is unreachable or times out (default: 5s), THE Incident Analysis Service SHALL continue analysis without historical context rather than failing.

### Requirement 4: Knowledge Store Persistence

**User Story:** As a platform operator, I want all incidents, analyses, resolutions, and relationships persisted to PostgreSQL, so that engineering knowledge accumulates over time and supports historical queries.

#### Acceptance Criteria

1. THE Knowledge Store Service SHALL maintain PostgreSQL tables: incident, incident_analysis, incident_resolution, incident_similarity, knowledge_reference, canonical_event, with Flyway-managed schema migrations.
2. WHEN an incident is stored, THE Knowledge Store Service SHALL persist all fields including: incident_id, service_name, description, severity, source, status, symptoms, metadata, raw_payload, exception_counts, exception_types, error_distribution, total_events, total_exceptions.
3. WHEN an analysis is stored, THE Knowledge Store Service SHALL persist: analysis_id, incident_id, category, root_cause, severity, confidence, confidence_percent, summary, business_impact, recommendations, evidence, llm_model, llm_provider, inference_time_ms, raw_response, status.
4. WHEN candidates are requested for similarity matching (GET /api/knowledge/candidates), THE Knowledge Store Service SHALL return incidents matching by service name OR overlapping exception types (PostgreSQL array overlap &&), excluding the specified incident, ordered by created_at DESC, limited to 50 results.
5. THE Knowledge Store Service SHALL provide paginated listing of incidents (GET /api/incidents) with default page=0, size=20, size capped at 100, and optional filters by service and status.
6. WHEN an incident is deleted, THE Knowledge Store Service SHALL cascade the deletion to all associated canonical_event records.
7. THE Knowledge Store Service SHALL enforce database constraints: severity ∈ {CRITICAL, HIGH, MEDIUM, LOW}, source ∈ {FILE_LOG, DOCKER_LOG, MANUAL, SIMULATOR, GRAFANA, API}, status ∈ {INGESTED, TRANSFORMING, ANALYZING, COMPLETE, FAILED}.
8. THE Knowledge Store Service SHALL maintain indexes on: service_name, status, created_at (DESC), exception_types (GIN), analysis incident_id, analysis category, similarity incident pairs, event incident_id, event_type, event level.
9. THE Knowledge Store Service SHALL store canonical events in batches of 1000 via JDBC batch updates regardless of incident size.
10. THE Knowledge Store Service SHALL expose a health endpoint that verifies PostgreSQL connectivity.

### Requirement 5: Similarity Matching

**User Story:** As a platform user, I want to find historically similar incidents, so that patterns and recurring issues can be identified and previous solutions can inform current analysis.

#### Acceptance Criteria

1. WHEN a similarity search is requested, THE Similarity Service SHALL compute a similarity score using weighted factors: service match (30%), exception type overlap via Jaccard index (35%), event category overlap via Jaccard index (15%), and error distribution similarity via cosine similarity (20%).
2. THE Similarity Service SHALL return similarity scores bounded within [0.0, 1.0] for all incident pairs.
3. THE Similarity Service SHALL produce symmetric similarity scores: score(A, B) == score(B, A).
4. THE Similarity Service SHALL exclude the queried incident from its own similarity results.
5. WHEN computing exception type overlap, THE Similarity Service SHALL use Jaccard index: |intersection| / |union|. Both empty sets SHALL return 1.0. One empty and one non-empty SHALL return 0.0.
6. WHEN computing error distribution similarity, THE Similarity Service SHALL use cosine similarity over percentage vectors. Both empty maps SHALL return 1.0. One empty and one non-empty SHALL return 0.0.
7. THE Similarity Service SHALL only return matches with a similarity score of 0.3 or greater.
8. THE Similarity Service SHALL return results sorted by similarity score in descending order.
9. THE Similarity Service SHALL accept a maxResults parameter (default: 5, max: 50) limiting the number of returned matches.
10. THE Similarity Service SHALL query the Knowledge Store for candidates pre-filtered by service name OR overlapping exception types, limited to 50 most recent candidates.
11. EACH returned ScoredMatch SHALL include: incidentId, service, analysisDate, similarityScore, rootCause, and matchReasons (at least one entry explaining the match).

### Requirement 6: AI Service Isolation

**User Story:** As a platform architect, I want AI inference isolated from business services behind an adapter, so that LLM providers can be swapped without modifying business logic.

#### Acceptance Criteria

1. THE AI Service SHALL expose a POST /api/ai/analyze endpoint accepting: prompt, model, provider (OLLAMA or OPENAI), and parameters (temperature, max_tokens, etc.).
2. WHEN provider is OLLAMA, THE AI Service SHALL call the local Ollama API at the configured URL with format: "json" to enforce JSON output.
3. THE AI Service SHALL support configurable timeouts per provider (default: 300s for Ollama, 60s for OpenAI).
4. WHEN the LLM returns a valid JSON response matching the expected schema, THE AI Service SHALL return a structured response with severity, rootCause, businessImpact, recommendations, summary, and structured=true.
5. WHEN the LLM returns non-JSON or malformed JSON, THE AI Service SHALL return the raw response text with structured=false.
6. THE AI Service SHALL expose GET /api/ai/models listing available models from all configured providers.
7. THE AI Service SHALL expose GET /api/ai/health reporting connectivity status to each configured provider.
8. THE AI Service SHALL record inference_time_ms for each analysis call and include it in the response.

### Requirement 7: Failure Simulation

**User Story:** As a platform developer, I want to generate realistic incidents without production dependencies, so that the platform can be tested, evaluated, and demonstrated in isolation.

#### Acceptance Criteria

1. THE Failure Simulator Service SHALL support scenarios: DATABASE_UNAVAILABLE, SERVICE_TIMEOUT, CONNECTION_REFUSED, NULL_POINTER_EXCEPTION, LATENCY_SPIKE, MEMORY_EXHAUSTION, THREAD_DEADLOCK, AUTHENTICATION_FAILURE, DISK_FULL, NETWORK_PARTITION.
2. WHEN a simulation is requested, THE Failure Simulator Service SHALL generate realistic log content matching the scenario's expected exception patterns (e.g., DATABASE_UNAVAILABLE produces ConnectionException and PSQLException).
3. THE Failure Simulator Service SHALL submit generated incidents through the normal ingestion pipeline (POST /api/incidents) with source=SIMULATOR.
4. THE Failure Simulator Service SHALL return a SimulationResult containing: simulationId, incidentId, scenario, targetService, generatedLogLines, simulatedAt, and status.
5. THE Failure Simulator Service SHALL accept a targetService parameter specifying which service name to use in the generated logs.
6. THE Failure Simulator Service SHALL accept a logLineCount parameter (default: 50, minimum: 1) controlling the number of log lines generated.
7. THE Failure Simulator Service SHALL expose GET /api/simulate/scenarios listing all available scenarios with descriptions.
8. THE Failure Simulator Service SHALL support batch simulation (POST /api/simulate/batch) generating multiple incidents from different scenarios in a single request.
9. EACH generated log line SHALL have a valid timestamp (within the last hour), a realistic log format with level and service name, and exception-specific content matching the scenario.

### Requirement 8: API Gateway and Routing

**User Story:** As a platform consumer, I want a single entry point for all platform APIs, so that I don't need to know individual service addresses or ports.

#### Acceptance Criteria

1. THE API Gateway SHALL route requests by path prefix: /api/incidents → Incident Ingestion Service, /api/knowledge → Knowledge Store Service, /api/similarity → Similarity Service, /api/simulate → Failure Simulator Service, /api/ai → AI Service, /api/analysis → Incident Analysis Service.
2. THE API Gateway SHALL expose a health aggregation endpoint (GET /api/health) that reports the combined health status of all downstream services.
3. THE API Gateway SHALL apply CORS headers allowing requests from any origin during MVP.
4. THE API Gateway SHALL log all incoming requests with method, path, response status, and duration.
5. THE API Gateway SHALL return HTTP 503 Service Unavailable if a downstream service is unreachable rather than a connection error.
6. THE API Gateway SHALL listen on port 8080 as the single externally-exposed port.

### Requirement 9: Docker Deployment

**User Story:** As a DevOps engineer, I want the entire platform deployable via a single docker-compose command, so that the system can be stood up quickly in any environment.

#### Acceptance Criteria

1. THE Platform SHALL provide a docker-compose.yml deploying all MVP services: postgres, redis, ollama, api-gateway, incident-ingestion, canonical-transformer, incident-analysis, knowledge-store, similarity-service, failure-simulator.
2. EACH service container SHALL expose a health endpoint that Docker can use for dependency ordering (healthcheck configuration).
3. THE docker-compose.yml SHALL define service dependencies such that services only start after their dependencies are healthy (depends_on with condition: service_healthy).
4. THE Platform SHALL use Docker network (eip-network) for inter-service communication, with only the API Gateway port (8080) exposed externally.
5. THE Platform SHALL externalize all configuration via environment variables, supporting override without rebuilding images.
6. EACH service SHALL have a Dockerfile that produces a minimal, runnable container image based on a JDK 17 base image.
7. THE Platform SHALL persist PostgreSQL data and Ollama models via named Docker volumes (pgdata, ollama_models).

### Requirement 10: Event-Driven Communication

**User Story:** As a platform architect, I want asynchronous event-driven communication between services, so that the ingestion pipeline can return immediately while analysis proceeds in the background.

#### Acceptance Criteria

1. THE Platform SHALL use Redis Streams as the Event Bus for asynchronous inter-service communication.
2. WHEN an incident is normalized, THE Canonical Transformer Service SHALL publish an INCIDENT_CREATED event to the Event Bus containing the full CanonicalIncidentEvent.
3. WHEN an analysis completes, THE Incident Analysis Service SHALL publish an ANALYSIS_COMPLETE event to the Event Bus containing the IncidentAnalysisResult.
4. WHEN a resolution is recorded, THE Knowledge Store Service SHALL publish a RESOLUTION_ADDED event to the Event Bus.
5. EACH event SHALL be wrapped in an EventEnvelope containing: eventId (UUID), eventType, publishedAt, sourceService, and payload.
6. THE Incident Analysis Service SHALL consume INCIDENT_CREATED events via a Redis Streams consumer group for reliable delivery.
7. IF Redis is unavailable, services SHALL fall back to synchronous processing (direct HTTP calls) and log a warning. The analysis pipeline SHALL NOT fail due to Event Bus unavailability.

### Requirement 11: Error Handling and Resilience

**User Story:** As a platform operator, I want robust error handling throughout the system, so that failures in one component do not cascade or prevent partial results from being delivered.

#### Acceptance Criteria

1. WHEN the AI Service is unavailable, THE Incident Analysis Service SHALL retry with exponential backoff (1s, 2s, 4s for 3 attempts) before marking the analysis as FAILED.
2. WHEN the Similarity Service is unavailable or times out, THE Incident Analysis Service SHALL proceed with analysis using only the base prompt (no historical context) and log a warning.
3. WHEN the Knowledge Store is unavailable during persistence, THE Platform SHALL retry with exponential backoff (1s, 2s, 4s for 3 attempts). If all retries fail, the service SHALL return the analysis result with a warning indicating persistence failure.
4. WHEN a service receives an invalid payload, it SHALL return HTTP 400 with descriptive validation errors without logging stack traces for expected validation failures.
5. WHEN an unhandled exception occurs in any service, THE service SHALL generate a correlation ID (UUID), log the error with the correlation ID, and return HTTP 500 with the correlation ID for troubleshooting.
6. THE API Gateway SHALL return HTTP 503 with a meaningful message when a downstream service is unhealthy or unreachable.
7. EACH service SHALL implement a circuit breaker pattern for calls to external services (AI, Knowledge Store, Similarity) to prevent cascade failures.

### Requirement 12: Data Model Validation

**User Story:** As a platform developer, I want strict validation on all data models at service boundaries, so that invalid data cannot corrupt the knowledge store or produce incorrect results.

#### Acceptance Criteria

1. THE Platform SHALL enforce that CanonicalEvent level is one of: ERROR, WARN, INFO, DEBUG, TRACE.
2. THE Platform SHALL enforce that CanonicalEvent eventType is one of: EXCEPTION, ERROR, WARNING, STACK_TRACE, INFO.
3. THE Platform SHALL enforce that CanonicalEvent message is non-null, non-empty, and at most 4000 bytes in UTF-8 encoding.
4. THE Platform SHALL enforce that incident severity is one of: CRITICAL, HIGH, MEDIUM, LOW.
5. THE Platform SHALL enforce that incident source is one of: FILE_LOG, DOCKER_LOG, MANUAL, SIMULATOR, GRAFANA, API.
6. THE Platform SHALL enforce that incident status transitions are forward-only: INGESTED → TRANSFORMING → ANALYZING → COMPLETE or FAILED.
7. THE Platform SHALL enforce that ScoredMatch similarityScore is between 0.0 and 1.0 inclusive.
8. THE Platform SHALL enforce that errorDistribution percentages sum to within 99.9 and 100.1 inclusive (±0.1 tolerance for floating point).
9. THE Platform SHALL enforce that exceptionTypes equals the key set of exceptionCounts for any given incident.
10. THE Platform SHALL perform all validation at service API boundaries using Jakarta Bean Validation annotations before processing.
