# AIRA — Automated Investigation and Response Algorithm

An engineering intelligence platform powered by RAG (Retrieval-Augmented Generation) that reads Jira incident tickets, builds a knowledge base, and generates AI-powered Root Cause Analysis with minimal LLM token usage.

## How It Works

```
Jira Ticket → Document Intelligence → Knowledge Store → RAG Retrieval → Ollama LLM → RCA Output
                (extract/normalize)     (pgvector DB)    (hybrid search)   (~177 tokens)
```

**Key principle:** The LLM never sees raw Jira data. It only receives compact, relevant context retrieved from the Knowledge Store.

## Architecture (Modular Monolith)

```
com.aira/
├── incident/       # Incident ingestion and lifecycle
├── knowledge/      # RAG knowledge store, embeddings, vector search
├── document/       # Document intelligence (Jira parsing + extraction)
├── intelligence/   # AI brain (RCA engine using RAG context only)
├── integration/    # External connectors (Jira, future: Confluence, Git)
└── common/         # Shared DTOs, enums
```

## Quick Start

### Prerequisites

- Java 17+
- PostgreSQL 17+ with pgvector extension
- Ollama with qwen3:1.7b model
- Maven (with CTOS Artifactory access via VPN)

### Setup

```bash
# Start PostgreSQL and create database
brew services start postgresql@17
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
createdb engineering_intelligence
psql -d engineering_intelligence -c "CREATE EXTENSION IF NOT EXISTS vector;"

# Ensure Ollama is running with model
brew services start ollama
ollama pull qwen3:1.7b

# Build
mvn clean compile
```

### Run

```bash
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=./truststore.jks -Djavax.net.ssl.trustStorePassword=changeit" \
  -Dspring-boot.run.arguments="--jira.api-token=YOUR_JIRA_API_TOKEN"
```

App starts at **http://localhost:8080**

### Usage

```bash
# Analyze a Jira ticket (end-to-end: fetch → process → store → retrieve → AI → RCA)
curl -X POST http://localhost:8080/api/analyze/jira/CM-5553

# Fetch a Jira ticket (raw, no analysis)
curl http://localhost:8080/api/jira/CM-5553

# Search knowledge base
curl 'http://localhost:8080/api/knowledge/search?service=cm&limit=5'

# Submit a manual incident
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "payment-service", "severity": "HIGH", "summary": "Timeout errors"}'
```

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/analyze/jira/{key}` | POST | Analyze a Jira ticket (full pipeline) |
| `/api/analyze` | POST | Analyze with custom input |
| `/api/jira/{key}` | GET | Fetch raw Jira ticket |
| `/api/jira/poll` | POST | Poll Jira for new tickets |
| `/api/knowledge/search` | GET | Search knowledge store |
| `/api/knowledge/documents` | POST | Store document |
| `/api/knowledge/similar` | GET | Find similar documents |
| `/api/incidents` | POST/GET | Manage incidents |
| `/actuator/health` | GET | Health check |

## Performance

| Metric | Result |
|--------|--------|
| Tokens per RCA request | ~177 (vs 3000+ raw) |
| Token reduction | **94%** |
| RCA response time | 6-7 seconds |
| Similar incident detection | ✅ Automatic via RAG |

## Tech Stack

- Java 17 + Spring Boot 3.3
- PostgreSQL 17 + pgvector (vector similarity search)
- Flyway (database migrations)
- Ollama (qwen3:1.7b for RCA, nomic-embed-text for embeddings)
- Maven

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_URL` | http://localhost:11434 | Ollama endpoint |
| `OLLAMA_MODEL` | qwen3:1.7b | LLM model for RCA |
| `OLLAMA_EMBED_MODEL` | nomic-embed-text | Embedding model |
| `JIRA_API_TOKEN` | (required) | Atlassian API token |
| `JIRA_BASE_URL` | https://ctosrepo.atlassian.net | Jira instance |
| `JIRA_USERNAME` | faiz@ctos.com.my | Jira username |
