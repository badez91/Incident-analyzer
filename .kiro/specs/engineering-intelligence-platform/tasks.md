# Implementation Plan: Engineering Intelligence Platform

## Overview

This plan implements the Engineering Intelligence Platform as a Maven multi-module project with Docker Compose deployment. Tasks are ordered to build infrastructure first (project structure, shared library, database), then core services (knowledge store, transformer, similarity, analysis), then integration services (ingestion, API gateway, failure simulator), and finally Docker deployment. Each service is independently deployable with its own Dockerfile and health endpoint.

The existing monolith at `/Users/faizfarhan/POC/Incident-analyzer/` continues to function during migration. The new platform is built in a sibling directory structure.

## Tasks

- [ ] 1. Create multi-module Maven project structure
  - [ ] 1.1 Create root project directory and parent POM
    - Create `/Users/faizfarhan/POC/engineering-intelligence-platform/` directory
    - Create parent `pom.xml` with `<modules>` for: eip-common, knowledge-store-service, canonical-transformer-service, similarity-service, incident-analysis-service, incident-ingestion-service, api-gateway, failure-simulator-service
    - Set parent to spring-boot-starter-parent 3.3.5, Java 17
    - Configure internal Artifactory repository (http://10.2.39.7:8080/artifactory/maven_ctos)
    - Add shared dependency management: Spring Boot starters, PostgreSQL, Flyway, Redis, Jackson, Validation
    - _Requirements: 9.5, 9.6_
  - [ ] 1.2 Create eip-common shared library module
    - Create `eip-common/pom.xml` (jar packaging, no Spring Boot plugin)
    - Create shared records: `CanonicalIncidentEvent`, `CanonicalEvent`, `ScoredMatch`, `IncidentAnalysisResult`, `RecommendedActions`, `Evidence`, `EventEnvelope`
    - Create shared DTOs: `IncidentSubmission`, `IngestionResponse`, `SimilaritySearchRequest`, `SimilaritySearchResult`, `SimulationRequest`, `SimulationResult`
    - Create shared enums: `Severity` (CRITICAL, HIGH, MEDIUM, LOW), `IncidentSource` (FILE_LOG, DOCKER_LOG, MANUAL, SIMULATOR, GRAFANA, API), `IncidentStatus` (INGESTED, TRANSFORMING, ANALYZING, COMPLETE, FAILED), `EventType` (EXCEPTION, ERROR, WARNING, STACK_TRACE, INFO), `LogLevel` (ERROR, WARN, INFO, DEBUG, TRACE)
    - Add Jakarta Validation annotations on all shared models
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.7_
  - [ ] 1.3 Create service module skeletons
    - For each service module: create `pom.xml` (depends on eip-common), `src/main/java` package structure, `src/main/resources/application.yml`, Spring Boot main class, and a `/health` endpoint returning 200
    - Service packages: `com.eip.knowledgestore`, `com.eip.transformer`, `com.eip.similarity`, `com.eip.analysis`, `com.eip.ingestion`, `com.eip.gateway`, `com.eip.simulator`
    - Each service runs on its designated port (8081-8086, gateway on 8080)
    - _Requirements: 1.8, 8.6, 9.2_

- [ ] 2. Implement Knowledge Store Service
  - [ ] 2.1 Configure PostgreSQL and Flyway for knowledge-store-service
    - Add spring-boot-starter-data-jpa, postgresql, flyway-core, flyway-database-postgresql dependencies
    - Configure application.yml with PostgreSQL connection (localhost:5432/engineering_intelligence), HikariCP pool (max 10), Flyway enabled
    - _Requirements: 4.1, 9.5_
  - [ ] 2.2 Create Flyway migration V1__create_platform_schema.sql
    - Create tables: incident, incident_analysis, incident_resolution, incident_similarity, knowledge_reference, canonical_event
    - Create all indexes: service_name, status, created_at DESC, exception_types (GIN), analysis incident_id, similarity pairs, reference incident_id, event incident_id/type/level
    - Add CHECK constraints: severity, source, status, level, event_type, analysis status
    - _Requirements: 4.1, 4.7, 4.8_
  - [ ] 2.3 Create JPA entities and repository layer
    - Create `IncidentEntity`, `IncidentAnalysisEntity`, `IncidentResolutionEntity`, `IncidentSimilarityEntity`, `KnowledgeReferenceEntity`, `CanonicalEventEntity`
    - Create Spring Data JPA repositories with custom queries: `findCandidatesForSimilarity` (native query with array overlap &&, exclude self, limit 50, order by created_at DESC)
    - Create mapper classes: Entity ↔ Domain model conversion
    - _Requirements: 4.2, 4.3, 4.4, 4.6_
  - [ ] 2.4 Implement Knowledge Store REST API
    - POST /api/knowledge/incidents — store incident with canonical events (batch insert 1000)
    - GET /api/knowledge/incidents/{id} — retrieve by UUID, 404 if not found
    - GET /api/knowledge/incidents — paginated list (page default 0, size default 20, max 100, filter by service/status)
    - POST /api/knowledge/analyses — store analysis result
    - GET /api/knowledge/analyses/{id} — retrieve analysis
    - GET /api/knowledge/candidates — pre-filtered similarity candidates
    - POST /api/knowledge/resolutions — store resolution
    - POST /api/knowledge/references — store knowledge reference
    - Health endpoint verifying PostgreSQL connectivity
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.9, 4.10_

- [ ] 3. Implement Canonical Transformer Service
  - [ ] 3.1 Create transformation logic
    - Port existing `CanonicalEventTransformer` logic from monolith into the new service module
    - Implement `transformToCanonical(IncidentSubmission)` — combine description + logSnippets, parse lines, produce CanonicalIncidentEvent
    - Implement timestamp extraction (4 supported formats), level detection (case-insensitive, WARNING→WARN, default INFO), service detection (FQ class name or brackets, default UNKNOWN), eventType classification
    - Implement message truncation at 4000 bytes UTF-8 boundary
    - Implement exception counting and error distribution calculation (percentages sum to 100.0)
    - Implement severity derivation: CRITICAL (>100 exceptions or OOM/StackOverflow), HIGH (>50 or >5 types), MEDIUM (>10), LOW
    - Implement symptom extraction from exception patterns
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.8, 2.9, 2.10_
  - [ ] 3.2 Create Transformer REST API and Event Bus publishing
    - POST /api/transform — accept IncidentSubmission, return CanonicalIncidentEvent
    - POST /api/transform/batch — batch transformation
    - POST /api/transform/logs — raw log content transformation
    - Integrate Redis Streams: publish INCIDENT_CREATED event after transformation
    - Include EventEnvelope wrapping (eventId, eventType, publishedAt, sourceService, payload)
    - Health endpoint
    - _Requirements: 2.7, 10.2, 10.5_

- [ ] 4. Implement Similarity Service
  - [ ] 4.1 Create similarity calculation logic
    - Port existing `SimilarityMatchingService` logic from monolith
    - Implement `searchSimilar(SimilaritySearchRequest)` — query candidates from Knowledge Store, score each, filter by threshold 0.3, sort descending, limit to maxResults
    - Implement weighted scoring: serviceMatch (0.30), exceptionTypeJaccard (0.35), eventCategoryJaccard (0.15), errorDistributionCosine (0.20)
    - Implement `jaccardIndex(Set, Set)` — |intersection|/|union|, 1.0 if both empty, 0.0 if one empty
    - Implement `cosineSimilarity(Map, Map)` — dot product / (norm1 * norm2), 1.0 if both empty, 0.0 if one empty
    - Implement `buildMatchReasons(candidate, request)` — explain which factors contributed, at least one entry
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11_
  - [ ] 4.2 Create Similarity Service REST API
    - POST /api/similarity/search — accept SimilaritySearchRequest, return SimilaritySearchResult with matches and candidatesEvaluated count
    - GET /api/similarity/score?incidentA={}&incidentB={} — calculate score between two specific incidents
    - Configure HTTP client for Knowledge Store calls (timeout: 5s)
    - Health endpoint
    - _Requirements: 5.9, 5.11_

- [ ] 5. Implement AI Service (Ollama/OpenAI Adapter)
  - [ ] 5.1 Create AI adapter with provider abstraction
    - Port existing `OllamaService` logic from monolith into provider pattern
    - Create `AiProvider` interface with `analyze(AiAnalysisRequest)` method
    - Create `OllamaProvider` implementation: POST to Ollama /api/generate with format:"json", stream:false
    - Create `OpenAiProvider` stub (future implementation, returns not-configured error)
    - Implement configurable timeouts per provider (Ollama: 300s, OpenAI: 60s)
    - Implement response parsing: attempt JSON parse → structured=true; fallback → rawResponse, structured=false
    - Record inference_time_ms for each call
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.8_
  - [ ] 5.2 Create AI Service REST API
    - POST /api/ai/analyze — accept AiAnalysisRequest, return AiAnalysisResponse
    - GET /api/ai/models — list available models from configured providers
    - GET /api/ai/health — report provider connectivity status
    - Health endpoint
    - _Requirements: 6.6, 6.7_

- [ ] 6. Checkpoint — verify core services compile and start independently
  - Build all modules: `mvn clean compile` from root
  - Start knowledge-store-service → verify Flyway migration + health endpoint
  - Start canonical-transformer-service → verify health endpoint
  - Start similarity-service → verify health endpoint
  - Start AI service → verify health endpoint + model listing

- [ ] 7. Implement Incident Analysis Service
  - [ ] 7.1 Create analysis pipeline orchestration
    - Implement Redis Streams consumer: listen for INCIDENT_CREATED events via consumer group
    - Implement full pipeline: store incident → find similar → build enhanced prompt → call AI → persist result → publish ANALYSIS_COMPLETE
    - Implement enhanced prompt building: base prompt + historical context (up to 5 similar incidents, capped at 8000 chars, omit null rootCause entries)
    - Implement exponential backoff retry for AI calls (1s, 2s, 4s, max 3 attempts)
    - Implement graceful degradation: if Similarity Service unavailable → proceed without history; if AI fails → mark FAILED
    - Handle LLM unparseable response: store raw text (truncated 32000), mark INCOMPLETE
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.10_
  - [ ] 7.2 Create Analysis Service REST API
    - POST /api/analysis — trigger analysis for a CanonicalIncidentEvent (synchronous mode)
    - GET /api/analysis/{incidentId} — retrieve analysis result from Knowledge Store
    - POST /api/analysis/{incidentId}/reanalyze — re-run analysis with different model/params
    - Publish ANALYSIS_COMPLETE event to Redis Streams on completion
    - Health endpoint
    - _Requirements: 3.8, 3.9, 10.3_
  - [ ] 7.3 Implement fallback to synchronous mode when Redis unavailable
    - If Redis connection fails, fall back to direct HTTP calls (skip event publishing)
    - Log warning when operating in synchronous fallback mode
    - _Requirements: 10.7, 11.2_

- [ ] 8. Implement Incident Ingestion Service
  - [ ] 8.1 Create ingestion logic and validation
    - Implement POST /api/incidents — validate serviceName (non-blank, max 255), description (non-blank, max 10000), source (valid enum or default MANUAL)
    - On valid payload: forward to Canonical Transformer (POST /api/transform), return 202 Accepted with incidentId
    - On invalid payload: return 400 Bad Request with validation errors and status REJECTED
    - Implement POST /api/incidents/batch — process each independently, return per-incident statuses
    - Configure HTTP client for Canonical Transformer calls (timeout: 10s)
    - Health endpoint
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_

- [ ] 9. Implement Failure Simulator Service
  - [ ] 9.1 Create scenario library and log generation
    - Implement `SimulationScenario` enum with 10 scenarios: DATABASE_UNAVAILABLE, SERVICE_TIMEOUT, CONNECTION_REFUSED, NULL_POINTER_EXCEPTION, LATENCY_SPIKE, MEMORY_EXHAUSTION, THREAD_DEADLOCK, AUTHENTICATION_FAILURE, DISK_FULL, NETWORK_PARTITION
    - For each scenario: create a log template with realistic timestamps (within last hour), appropriate exception classes, stack traces, and service-specific content
    - Implement `generateLogs(scenario, targetService, logLineCount)` — produce logLineCount lines matching scenario patterns
    - _Requirements: 7.1, 7.2, 7.5, 7.6, 7.9_
  - [ ] 9.2 Create Simulator REST API
    - POST /api/simulate — generate incident and submit through ingestion pipeline (POST /api/incidents with source=SIMULATOR), return SimulationResult
    - GET /api/simulate/scenarios — list all scenarios with descriptions
    - POST /api/simulate/batch — multiple simulations in one request
    - Health endpoint
    - _Requirements: 7.3, 7.4, 7.7, 7.8_

- [ ] 10. Implement API Gateway
  - [ ] 10.1 Create gateway routing and cross-cutting concerns
    - Configure Spring Cloud Gateway or simple reverse proxy routing:
      - /api/incidents → incident-ingestion-service:8081
      - /api/knowledge → knowledge-store-service:8084
      - /api/similarity → similarity-service:8085
      - /api/simulate → failure-simulator-service:8086
      - /api/ai → ai-service (embedded or port 8083)
      - /api/analysis → incident-analysis-service:8083
    - Implement health aggregation (GET /api/health) — check all downstream services
    - Add CORS headers (allow all origins for MVP)
    - Add request logging (method, path, status, duration)
    - Return 503 when downstream service unreachable
    - Listen on port 8080
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 11. Implement global error handling and resilience
  - [ ] 11.1 Add error handling to each service
    - Create `@ControllerAdvice` in each service: correlation ID generation, validation error formatting, 500 handler with correlation ID
    - Add circuit breaker pattern (Resilience4j) for inter-service HTTP calls
    - Implement retry with exponential backoff for Knowledge Store persistence (1s, 2s, 4s, 3 attempts)
    - Return warnings (not errors) to callers when non-critical operations fail (e.g., persistence after analysis)
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_
  - [ ] 11.2 Add data model validation at service boundaries
    - Enforce Jakarta Bean Validation on all @RequestBody parameters
    - Implement status transition validation (forward-only: INGESTED→TRANSFORMING→ANALYZING→COMPLETE/FAILED)
    - Validate errorDistribution sum (±0.1), exceptionTypes == exceptionCounts.keySet()
    - Return 400 with descriptive messages for validation failures
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8, 12.9, 12.10_

- [ ] 12. Checkpoint — verify all services compile and start
  - Build entire project: `mvn clean package -DskipTests`
  - Start each service individually and verify health endpoints
  - Test end-to-end flow manually: submit incident → verify it reaches Knowledge Store

- [ ] 13. Create Docker deployment
  - [ ] 13.1 Create Dockerfiles for each service
    - Create `Dockerfile` in each service module: multi-stage build (maven build → JDK 17 runtime), expose service port, set ENTRYPOINT
    - Ensure each Dockerfile copies the service JAR and sets Spring profile to "docker"
    - _Requirements: 9.6_
  - [ ] 13.2 Create docker-compose.yml
    - Define services: postgres (15-alpine), redis (7-alpine), ollama, api-gateway, incident-ingestion, canonical-transformer, incident-analysis, knowledge-store, similarity-service, failure-simulator
    - Configure healthchecks for all services
    - Configure depends_on with condition: service_healthy
    - Create eip-network Docker network, only expose port 8080 externally
    - Define volumes: pgdata, ollama_models
    - Externalize all configuration via environment variables
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.7_
  - [ ] 13.3 Create docker-compose environment and startup scripts
    - Create `.env` file with defaults (DB_PASSWORD, OLLAMA_MODEL, etc.)
    - Create `start.sh` script: docker-compose up -d, wait for health, print status
    - Create `stop.sh` script: docker-compose down
    - Create README with deployment instructions
    - _Requirements: 9.5_

- [ ] 14. Integration testing and final verification
  - [ ] 14.1 End-to-end flow test
    - Start full Docker Compose stack
    - Submit incident via API Gateway → verify 202 response
    - Wait for async processing → query Knowledge Store for incident and analysis
    - Run failure simulator → verify incident created and analyzed
    - Query similarity endpoint → verify results
    - _Requirements: 1.1, 3.1, 4.2, 5.1, 7.3_
  - [ ] 14.2 Error scenario verification
    - Test with AI service stopped → verify analysis marks FAILED after retries
    - Test with invalid payload → verify 400 responses with descriptive errors
    - Test with Knowledge Store stopped → verify graceful degradation
    - Test gateway with service down → verify 503 response
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [ ] 15. Final checkpoint — full platform operational
  - All services start via docker-compose
  - End-to-end incident flow works (ingest → transform → analyze → store)
  - Failure simulator generates incidents that get analyzed
  - Similarity search returns results for incidents with overlapping characteristics
  - API Gateway routes all requests correctly
  - Health endpoint reports aggregate status

## Notes

- The existing monolith at `/Users/faizfarhan/POC/Incident-analyzer/` remains functional during migration
- Each service can be developed and tested independently before Docker integration
- Redis is used for Event Bus (Redis Streams) — install via `brew install redis` for local development
- The AI service can be embedded within the incident-analysis-service for MVP simplicity, then extracted later
- Post-MVP services (Jira, Confluence, Source Code Analysis) are not included in this task list — they will have their own spec
- Maven builds use internal Artifactory at http://10.2.39.7:8080/artifactory/maven_ctos (requires VPN)

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Wave 1: Foundation",
      "tasks": [1]
    },
    {
      "name": "Wave 2: Core Services",
      "tasks": [2, 3, 4, 5]
    },
    {
      "name": "Wave 3: Core Checkpoint",
      "tasks": [6]
    },
    {
      "name": "Wave 4: Integration Services",
      "tasks": [7, 8, 9]
    },
    {
      "name": "Wave 5: Gateway & Resilience",
      "tasks": [10, 11]
    },
    {
      "name": "Wave 6: Verification",
      "tasks": [12]
    },
    {
      "name": "Wave 7: Docker Deployment",
      "tasks": [13]
    },
    {
      "name": "Wave 8: Final Testing",
      "tasks": [14, 15]
    }
  ]
}
```
