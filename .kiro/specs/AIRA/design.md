# Design Document: AIRA — Automated Investigation and Response Algorithm

## Overview

AIRA is a modular monolith Spring Boot application designed as an autonomous engineering investigation system. It gathers context from multiple sources (knowledge base, Confluence, server logs, source code), then produces investigation reports with hypotheses and identifies what information is missing — rather than declaring definitive root causes without sufficient evidence.

**Core principles:**
- The LLM NEVER receives raw Jira or Confluence data — only pre-processed RAG context
- When evidence is insufficient, AIRA asks for more information rather than guessing
- Output is an Investigation Report (hypothesis + missing info + next steps), not a definitive RCA

**Architecture style:** Modular Monolith — single deployable, domain-separated packages, future microservice extraction ready.

## Architecture

### Domain Structure

```
com.aira/
├── incident/       # Incident ingestion, lifecycle, simulation
├── knowledge/      # Brain memory: RAG store, embeddings, vector search
├── document/       # Document intelligence: parsing, extraction (NO AI)
├── intelligence/   # AIRA core reasoning: RCA, bug prediction, risk scoring
├── integration/    # System connectors: Jira, Confluence, Git (NO AI)
├── agent/          # Future: autonomous investigation agents
├── reporting/      # Future: MTTR, trends, KPIs, dashboards
└── common/         # Shared DTOs, enums, exceptions
```

### Domain Communication Rules

```
✅ Allowed:
  Integration → Document Intelligence → Knowledge
  Intelligence → Knowledge (read only, via RAG)
  Incident → Document Intelligence
  Incident → Intelligence
  Agent → Knowledge + Intelligence + Integration (orchestration)

❌ Not Allowed:
  Integration → Intelligence (must go through Knowledge first)
  Intelligence → Integration (LLM never sees raw external data)
  Knowledge → Incident (no reverse dependency)
  Agent → Database directly (must use service interfaces)
  Any domain → another domain's repository directly
```

### System Flow

```
INGESTION FLOW:
Jira → Integration → Document Intelligence → Knowledge Store → Embedding → pgvector

QUERY FLOW:
Incident Input → Metadata Extraction → RAG Retrieval (Hybrid Search) → Intelligence Engine (LLM) → RCA Output
```

### RAG Design (Critical)

```
❌ WRONG: Send full Jira ticket + all comments + attachments to LLM
✅ CORRECT:
  Step 1: Extract metadata (service, environment, exceptionType, labels, components)
  Step 2: Retrieve relevant context only (Top 5 similar incidents, Top 3 RCA docs)
  Step 3: Build compact LLM context (Incident Summary + Historical Incidents + Known Patterns)
  Step 4: Send to LLM (< 2000 tokens, high signal, low noise)
```

## Components and Interfaces

### 1. Integration Domain (`com.aira.integration.jira`)

**JiraConnector** — WebClient-based Jira REST API client. NO AI logic here.

| Method | Signature | Description |
|--------|-----------|-------------|
| getIssue | `Optional<JiraIssueDto> getIssue(String key)` | Fetches a single Jira issue by key |
| searchIssues | `List<JiraIssueDto> searchIssues(String jql, int maxResults)` | Searches Jira via JQL |

**JiraController** — REST endpoints.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/jira/{key}` | GET | Fetch a specific Jira ticket |
| `/api/jira/poll` | POST | Search Jira with JQL query |

### 2. Document Intelligence Domain (`com.aira.document.service`)

**ContentExtractor** — Pure parsing/extraction logic. NO AI.

| Method | Description |
|--------|-------------|
| `extractExceptionTypes(text)` | Regex extraction of Java exception class names |
| `extractErrorBehaviors(text)` | Keyword matching for error behaviors |
| `extractComponents(text)` | Keyword matching for system components |
| `extractServiceName(jiraKey)` | Extracts service prefix from Jira key |
| `buildSearchableText(...)` | Builds truncated searchable text (max 2000 chars) |
| `buildSummary(...)` | Builds compact document summary (max 500 chars) |

**DocumentIntelligenceService** — Orchestrates Jira → Knowledge pipeline.

| Method | Description |
|--------|-------------|
| `processJiraTicket(...)` | Full pipeline: extract → normalize → store in Knowledge |

### 3. Knowledge Domain (`com.aira.knowledge`) — CORE OF AIRA

**KnowledgeService** — Brain memory operations.

| Method | Description |
|--------|-------------|
| `storeDocument(doc)` | Persists an engineering document |
| `searchByMetadata(service, exception, limit)` | SQL-based metadata search |
| `findByReference(referenceId)` | Lookup by external reference ID |
| `hybridSearch(service, exception, components, maxResults)` | Combined metadata + component filtering + future vector |

**EmbeddingService** — Generates vector embeddings via Ollama nomic-embed-text.

| Method | Description |
|--------|-------------|
| `generateEmbedding(text)` | 768-dim embedding, returns null if unavailable |

**KnowledgeController** — REST endpoints.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/knowledge/search` | GET | Hybrid search by service/exception |
| `/api/knowledge/documents` | POST | Store a new document |
| `/api/knowledge/similar` | GET | Find similar documents by referenceId |

### 4. Intelligence Domain (`com.aira.intelligence`) — AIRA CORE REASONING

**RcaEngine** — Orchestrates full RCA pipeline using ONLY RAG context.

| Method | Description |
|--------|-------------|
| `analyze(incidentId, serviceName, summary, exceptionType, components)` | Full: retrieve → prompt → LLM → parse |

**PromptBuilder** — Token-efficient prompt construction.

| Method | Description |
|--------|-------------|
| `buildRcaPrompt(serviceName, summary, exceptionType, context)` | Structured prompt (<8000 chars / ~2000 tokens) |

**OllamaClient** — WebClient-based Ollama API client.

| Method | Description |
|--------|-------------|
| `analyze(prompt, model)` | Sends prompt to Ollama, returns Optional<String> |

**ResponseParser** — Parses LLM responses with JSON + free-text fallback.

| Method | Description |
|--------|-------------|
| `parseRcaResponse(rawResponse, incidentId, context, inferenceMs)` | JSON parse → structured RcaResult |

**IntelligenceController** — REST endpoints.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/analyze` | POST | Analyze with custom input |
| `/api/analyze/jira/{key}` | POST | Full pipeline: Jira → DocIntel → Knowledge → RAG → RCA |
| `/api/analyze/{incidentId}` | GET | Retrieve stored RCA |

### 5. Incident Domain (`com.aira.incident`)

**IncidentService** — Lifecycle management.

| Method | Description |
|--------|-------------|
| `createIncident(...)` | Creates and persists an incident |
| `updateStatus(id, newStatus)` | Updates incident status |
| `findById(id)` | Retrieves an incident |
| `search(service, status)` | Searches with optional filters |

**IncidentController** — REST endpoints.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/incidents` | POST | Create a new incident |
| `/api/incidents/{id}` | GET | Get incident by ID |
| `/api/incidents` | GET | Search incidents |

### 6. Common Domain (`com.aira.common`)

**DTOs:** RcaResult, RetrievedContext (records)
**Enums:** Severity, SourceType
**Exceptions:** GlobalExceptionHandler, ResourceNotFoundException, ServiceUnavailableException

### 7. Agent Domain (`com.aira.agent`) — FUTURE

Placeholder package for autonomous agents. Agents orchestrate via service interfaces only.

### 8. Reporting Domain (`com.aira.reporting`) — FUTURE

Placeholder package for MTTR, trends, KPIs, dashboards.

## Data Models

### EngineeringDocument (Knowledge Domain — Canonical Model)

```java
@Entity
@Table(name = "engineering_document")
public class EngineeringDocumentEntity {
    UUID id;
    String sourceType;           // JIRA, CONFLUENCE, GIT, INCIDENT, MANUAL
    String referenceId;          // e.g., CM-5553
    String summary;              // Max 500 chars
    String serviceName;
    String environment;
    String exceptionType;
    String components;           // JSONB
    String structuredMetadata;   // JSONB
    String searchableText;       // Max 2000 chars
    float[] embeddingVector;     // pgvector(768) via nomic-embed-text
    Instant createdAt;
    Instant updatedAt;
}
```

### IncidentEntity (Incident Domain)

```java
@Entity
@Table(name = "incident")
public class IncidentEntity {
    UUID id;
    String serviceName;          // Required
    String severity;             // CRITICAL, HIGH, MEDIUM, LOW
    String status;               // INGESTED, ANALYZING, RESOLVED, CLOSED
    String summary;
    String source;               // MANUAL, JIRA, SIMULATOR
    String jiraKey;
    String metadata;             // JSONB
    Instant createdAt;
    Instant updatedAt;
}
```

### RcaResult (Intelligence Domain Output)

```java
public record RcaResult(
    UUID incidentId,
    String severity,
    String rootCause,
    int confidencePercent,
    List<String> businessImpact,
    Map<String, List<String>> recommendations,
    String summary,
    List<RetrievedContext> contextUsed,
    int tokensUsed,
    long inferenceTimeMs,
    Instant analyzedAt
) {}
```

### JiraIssueDto (Integration Domain)

```java
public record JiraIssueDto(
    String key, String summary, String description,
    String priority, String status, String assignee,
    List<String> labels, List<String> comments,
    String created, String updated
) {}
```

## Database Schema

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

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
```

## Error Handling

| Layer | Strategy |
|-------|----------|
| Controller | `@Valid` on request bodies; `GlobalExceptionHandler` catches all exceptions |
| Service | Returns `Optional` for nullable lookups; logs warnings for non-critical failures |
| External clients | Returns `Optional.empty()` on failure; never throws to callers |
| Validation | Jakarta Bean Validation (`@NotBlank`) on request DTOs |

## Correctness Properties

Property 1: The LLM NEVER receives raw Jira/Confluence data — only pre-processed RAG context from the Knowledge Store.
**Validates: Requirements 5.3**

Property 2: Prompts are capped at <2000 tokens. Actual observed usage is ~153-177 tokens per request (>90% reduction).
**Validates: Requirements 5.5**

Property 3: If Ollama is down, analysis returns a fallback result (not an error). If Jira is down, endpoints return 404/empty.
**Validates: Requirements 5.8**

Property 4: Documents are stored in Knowledge BEFORE RCA runs — the knowledge base always has the data the AI references.
**Validates: Requirements 5.2**

Property 5: No domain accesses another domain's repository directly. Communication only via service interfaces.
**Validates: Requirements 1.4**

## Testing Strategy

| Level | Scope | Tools |
|-------|-------|-------|
| Unit | ContentExtractor, ResponseParser, IncidentService | JUnit 5, Mockito, AssertJ |
| Integration | Spring context loads, H2 database | @SpringBootTest, @ActiveProfiles("test"), H2 |
| End-to-end | Full pipeline: Jira → Knowledge → RAG → RCA | curl against running instance |

## Performance (Observed)

| Metric | Target | Actual |
|--------|--------|--------|
| Token reduction | >90% | 94% (~153-177 tokens vs 3000+ raw) |
| RCA response time | <10s | 5-7 seconds |
| Retrieval time | <500ms | <100ms (metadata), future <500ms (vector) |
| Build time | — | 1.2s (forked JDK 17) |
| Tests (20) | — | ~2s |

## Tech Stack

- Java 17 + Spring Boot 3.3.5
- PostgreSQL 17 + pgvector extension
- Flyway (database migrations)
- Ollama (qwen3:1.7b for RCA, nomic-embed-text for embeddings)
- Lombok 1.18.32
- Maven (forked JDK 17 compilation)
- JUnit 5 + Mockito + AssertJ + H2 (testing)

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_URL` | http://localhost:11434 | Ollama endpoint |
| `OLLAMA_MODEL` | qwen3:1.7b | LLM model for RCA |
| `OLLAMA_EMBED_MODEL` | nomic-embed-text | Embedding model |
| `OLLAMA_TIMEOUT` | 120 | Timeout in seconds |
| `JIRA_BASE_URL` | https://ctosrepo.atlassian.net | Jira instance |
| `JIRA_USERNAME` | faiz@ctos.com.my | Jira username |
| `JIRA_API_TOKEN` | (required) | Atlassian API token |

## AIRA Evolution Roadmap

| Phase | Capability | Status |
|-------|-----------|--------|
| Phase 1 | Incident management, Jira integration, Document Intelligence | ✅ Complete |
| Phase 2 | Knowledge Store + RAG, RCA Engine, Hybrid Search | ✅ Complete |
| Phase 3 | pgvector embeddings, vector similarity search | 🔄 Partial (schema ready, Hibernate mapping pending) |
| Phase 4 | Confluence integration, Git source code analysis | 📋 Planned |
| Phase 5 | Agent domain (Incident Agent, Release Agent) | 📋 Planned |
| Phase 6 | Reporting domain (MTTR, trends, KPIs) | 📋 Planned |
| Phase 7 | Bug prediction, Architecture recommendations | 📋 Planned |
| Phase 8 | Engineering Intelligence Copilot | 🔮 Vision |
