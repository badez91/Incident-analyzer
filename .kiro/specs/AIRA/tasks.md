# Implementation Plan: AIRA — Automated Investigation and Response Algorithm

## Overview

This plan reflects AIRA as a modular monolith Spring Boot platform for autonomous engineering investigation. AIRA produces investigation reports with hypotheses and missing information — not definitive RCA. Tasks cover the implemented work and future roadmap.

## Tasks

- [x] 1. Project foundation
  - [x] 1.1 Create Spring Boot application with Maven build
    - Created `pom.xml` with Spring Boot 3.3.5 parent, Java 17
    - Dependencies: web, data-jpa, postgresql, flyway, webflux, validation, actuator, lombok, test, h2
    - Configured maven-compiler-plugin with forked JDK 17 (`/opt/homebrew/opt/openjdk@17`)
    - _Requirements: 1.1, 10.4_
  - [x] 1.2 Create database schema migration (Flyway)
    - `V1__create_eip_schema.sql`: engineering_document (pgvector), incident, rca_result
    - Indexes, constraints, pgvector extension
    - _Requirements: 3.1, 3.9_
  - [x] 1.3 Configure application.yml
    - PostgreSQL, HikariCP, Flyway, Ollama, Jira, Actuator
    - _Requirements: 10.1, 10.2_

- [x] 2. Common domain (`com.aira.common`)
  - [x] 2.1 Create shared DTOs: RcaResult, RetrievedContext
  - [x] 2.2 Create shared enums: Severity, SourceType
  - [x] 2.3 Create global exception handling: GlobalExceptionHandler, ResourceNotFoundException, ServiceUnavailableException
    - _Requirements: 1.3, 9.1, 9.2, 9.3, 9.4_

- [x] 3. Incident domain (`com.aira.incident`)
  - [x] 3.1 IncidentEntity with JPA mapping + Lombok
  - [x] 3.2 IncidentRepository (findByServiceName, findByJiraKey, findByStatus)
  - [x] 3.3 IncidentService (create, updateStatus, findById, search)
  - [x] 3.4 IncidentController with @Valid validation
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 4. Integration domain (`com.aira.integration.jira`)
  - [x] 4.1 JiraConnector (WebClient, Basic Auth, graceful degradation)
  - [x] 4.2 JiraIssueDto record
  - [x] 4.3 JiraController (GET /{key}, POST /poll)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 5. Document Intelligence domain (`com.aira.document.service`)
  - [x] 5.1 ContentExtractor (exceptions, behaviors, components, service name, searchable text, summary)
  - [x] 5.2 DocumentIntelligenceService (processJiraTicket pipeline, NO AI logic)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

- [x] 6. Knowledge domain (`com.aira.knowledge`)
  - [x] 6.1 EngineeringDocumentEntity with Lombok
  - [x] 6.2 EngineeringDocumentRepository (native queries for metadata search)
  - [x] 6.3 KnowledgeService (store, searchByMetadata, findByReference, hybridSearch)
  - [x] 6.4 EmbeddingService (Ollama nomic-embed-text, graceful degradation)
  - [x] 6.5 KnowledgeController (search, documents, similar)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [x] 7. Intelligence domain (`com.aira.intelligence`)
  - [x] 7.1 OllamaClient (Optional<String> response, configurable timeout)
  - [x] 7.2 PromptBuilder (structured prompt <2000 tokens)
  - [x] 7.3 ResponseParser (JSON + free-text fallback + null safety)
  - [x] 7.4 RcaEngine (retrieve → prompt → LLM → parse orchestration)
  - [x] 7.5 IntelligenceController (POST /analyze, POST /analyze/jira/{key})
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11_

- [x] 8. Testing
  - [x] 8.1 Test configuration (application-test.yml, H2, Flyway disabled)
  - [x] 8.2 Unit tests: ContentExtractorTest (10), ResponseParserTest (4), IncidentServiceTest (5)
  - [x] 8.3 Integration test: AiraApplicationTest (Spring context loads)
  - [x] 8.4 End-to-end verified: POST /api/analyze/jira/CM-5553 → 95% confidence, 156 tokens, 5.8s
    - _Requirements: 11.1, 11.2, 11.3_

- [ ] 9. Phase 3: pgvector embedding activation
  - [ ] 9.1 Map embeddingVector in EngineeringDocumentEntity (Hibernate pgvector support)
  - [ ] 9.2 Generate and store embeddings on document creation in DocumentIntelligenceService
  - [ ] 9.3 Add native query for vector cosine similarity: `ORDER BY embedding_vector <=> ?`
  - [ ] 9.4 Implement true hybrid search: combine metadata score (0.4) + vector score (0.6)
  - [ ] 9.5 Add IVFFlat index on embedding_vector for performance
    - _Requirements: 3.5, 3.6, 3.9, 3.10_

- [ ] 10. Phase 3: RCA result persistence
  - [ ] 10.1 Create RcaResultEntity and RcaResultRepository
  - [ ] 10.2 Store RCA results after each analysis
  - [ ] 10.3 Implement GET /api/analyze/{incidentId} to retrieve stored results
  - [ ] 10.4 Link RCA results to incidents for historical tracking
    - _Requirements: 3.8, 5.9_

- [ ] 11. Phase 4: Confluence integration
  - [ ] 11.1 Create ConfluenceConnector in integration domain
  - [ ] 11.2 Search Confluence by service name + exception types
  - [ ] 11.3 Query runbooks, architecture docs, troubleshooting guides
  - [ ] 11.4 Process Confluence pages via Document Intelligence → Knowledge Store
  - [ ] 11.5 Include documentation context in RAG retrieval (capped at 4000 chars)
    - _Requirements: 6.7_

- [ ] 12. Phase 4: Git source code analysis
  - [ ] 12.1 Create GitConnector in integration domain
  - [ ] 12.2 Parse stack traces to extract file:line references
  - [ ] 12.3 Read source files from configured repository paths
  - [ ] 12.4 Include code context in RAG prompt (capped at 3000 chars)
    - _Requirements: 6.7_

- [ ] 13. Phase 4: Advanced Document Intelligence
  - [ ] 13.1 ADF parsing (Jira Atlassian Document Format)
  - [ ] 13.2 Table extraction from Jira descriptions
  - [ ] 13.3 Attachment OCR/vision (future: parse screenshots and images)
  - [ ] 13.4 Confluence wiki markup parsing
    - _Requirements: 4.9_

- [ ] 14. Phase 5: Agent domain
  - [ ] 14.1 Create `com.aira.agent` package structure
  - [ ] 14.2 Define Agent interface (orchestrates Knowledge + Intelligence + Integration)
  - [ ] 14.3 Implement IncidentAgent (autonomous investigation workflow)
  - [ ] 14.4 Implement ReleaseAgent (release risk analysis)
  - [ ] 14.5 Implement ArchitectureAgent (system health analysis)
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [ ] 15. Phase 6: Reporting domain
  - [ ] 15.1 Create `com.aira.reporting` package structure
  - [ ] 15.2 Implement MTTR analysis
  - [ ] 15.3 Implement incident trends (service, severity, recurrence)
  - [ ] 15.4 Implement engineering KPIs
  - [ ] 15.5 Create dashboard endpoints
    - _Requirements: 8.1, 8.2, 8.3_

- [ ] 16. Phase 7: Intelligence evolution
  - [ ] 16.1 Bug prediction engine (pattern-based from historical incidents)
  - [ ] 16.2 Architecture recommendations (based on recurring issues)
  - [ ] 16.3 Risk scoring engine (severity × frequency × blast radius)
    - _Requirements: 5.12_

- [ ] 17. Phase 8: Docker deployment
  - [ ] 17.1 Create Dockerfile (multi-stage, JDK 17 runtime)
  - [ ] 17.2 Create docker-compose.yml (AIRA + PostgreSQL + Ollama)
  - [ ] 17.3 Environment configuration and startup scripts

## Notes

- AIRA uses forked JDK 17 compilation (`/opt/homebrew/opt/openjdk@17`) because system default is JDK 26 (incompatible with Lombok).
- Maven builds require VPN for CTOS Artifactory access.
- Ollama must be running locally with qwen3:1.7b and nomic-embed-text models.
- PostgreSQL 17 with pgvector extension required. Embedding column exists in schema but Hibernate mapping is Phase 3 work.
- The truststore.jks is required at runtime for Jira HTTPS connectivity.
- Each domain is designed for independent extraction — no cross-domain repository access, DTOs at boundaries.
- Agent domain (Phase 5) agents MUST NOT access databases directly — only via service interfaces.
- AIRA's final vision: Automated Incident Investigator → Engineering Knowledge Brain → Bug Prediction System → Architecture Advisor → Engineering Intelligence Copilot.

## Verification

All completed tasks verified:
- `mvn clean compile` — BUILD SUCCESS (1.3s, forked JDK 17)
- `mvn test` — 20 tests, 0 failures
- End-to-end: `POST /api/analyze/jira/CM-5553` — RCA generated (95% confidence, 156 tokens, 5.8s)

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Phase 1-2: Foundation + Core (COMPLETE)",
      "tasks": [1, 2, 3, 4, 5, 6, 7, 8],
      "status": "complete"
    },
    {
      "name": "Phase 3: Embeddings + Persistence",
      "tasks": [9, 10],
      "status": "next"
    },
    {
      "name": "Phase 4: External Integrations + Advanced DocIntel",
      "tasks": [11, 12, 13],
      "status": "planned"
    },
    {
      "name": "Phase 5: Agent Domain",
      "tasks": [14],
      "status": "planned"
    },
    {
      "name": "Phase 6: Reporting Domain",
      "tasks": [15],
      "status": "planned"
    },
    {
      "name": "Phase 7: Intelligence Evolution",
      "tasks": [16],
      "status": "planned"
    },
    {
      "name": "Phase 8: Deployment",
      "tasks": [17],
      "status": "planned"
    }
  ]
}
```
