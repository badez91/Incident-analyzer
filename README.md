# AIRA — Automated Investigation and Response Algorithm

An autonomous engineering investigation platform powered by RAG (Retrieval-Augmented Generation). AIRA investigates Jira incident tickets by gathering context from multiple sources — knowledge base, Confluence docs, server logs, and source code — then produces an investigation report with hypotheses, missing information, and next steps.

**AIRA is NOT an RCA tool.** It is an investigation system that identifies what it knows, what it doesn't know, and what you need to find out.

## How It Works

```
Jira Ticket → Document Intelligence → Knowledge Store → RAG Context Gathering → Investigation
                                              ↓                    ↓
                                       Confluence Docs      Server Logs + Source Code
                                              ↓                    ↓
                                         LLM Analysis ← All Context Combined (~170 tokens)
                                              ↓
                                    Investigation Report (hypothesis + missing info + next steps)
```

**Key principles:**
- The LLM NEVER sees raw Jira data — only pre-processed context from the Knowledge Store
- When evidence is insufficient, AIRA asks for more info rather than guessing
- Output is a hypothesis with confidence level, not a definitive root cause

## Architecture (Modular Monolith)

```
com.aira/
├── incident/           # Incident ingestion and lifecycle
├── knowledge/          # RAG knowledge store, embeddings, full-text search
├── document/           # Document intelligence (Jira parsing, attachment OCR, extraction)
├── intelligence/       # Investigation engine (hypothesis generation, NOT definitive RCA)
├── integration/
│   ├── jira/           # Jira connector (fetch tickets, search similar resolved)
│   ├── confluence/     # Confluence connector (search related docs/runbooks)
│   ├── logs/           # Log reader (local folder grep, future: SSH)
│   └── sourcecode/     # Source code context (local repo, stack trace lookup)
├── agent/              # Future: autonomous investigation agents
├── reporting/          # Future: MTTR, trends, KPIs
└── common/             # DTOs, enums, global exception handling
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
# Using run script (recommended)
chmod +x run.sh
./run.sh

# Or manually
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=./truststore.jks -Djavax.net.ssl.trustStorePassword=changeit" \
  -Dspring-boot.run.arguments="--jira.api-token=YOUR_TOKEN --confluence.api-token=YOUR_TOKEN"
```

App starts at **http://localhost:8080** (includes demo UI)

### Usage

```bash
# Investigate a Jira ticket (full pipeline: fetch → process → search similar → gather context → investigate)
curl -X POST http://localhost:8080/api/analyze/jira/CM-5553

# Manual investigation
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "payment-service", "summary": "Timeout errors in batch processing"}'

# Search Confluence for related docs
curl 'http://localhost:8080/api/confluence/search?keywords=eTR,batch&maxResults=3'

# Search server logs
curl 'http://localhost:8080/api/logs/search?service=cm&keywords=error&minutesBack=60'

# Fetch a Jira ticket
curl http://localhost:8080/api/jira/CM-5553

# Search knowledge base
curl 'http://localhost:8080/api/knowledge/search?service=cm&limit=5'
```

## Investigation Output

AIRA produces an **Investigation Report**, not a definitive RCA:

```json
{
  "status": "NEEDS_INFO",
  "hypothesis": "Possible batch processing failure due to file path resolution after deployment",
  "confidencePercent": 40,
  "evidenceFound": ["Batch upload error mentioned", "Customer not updated error"],
  "missingInfo": [
    "Server logs with stack trace from the time of failure",
    "Deployment history — was there a release before this occurred?",
    "Full error output from the batch processor"
  ],
  "questionsToAsk": [
    "Was there a deployment or configuration change before this incident?",
    "Is this still occurring or has it self-recovered?",
    "Can you provide the batch processor logs?"
  ],
  "nextSteps": [
    "Collect server logs from the affected timeframe",
    "Check deployment history for recent changes",
    "Verify batch processor working directory configuration"
  ],
  "recommendations": {
    "immediate": ["Gather more information before taking action"],
    "shortTerm": ["Review batch processor configuration"],
    "longTerm": ["Add monitoring for batch processing failures"]
  }
}
```

**Status levels:**
- `CONFIRMED` — High confidence, sufficient evidence (80%+)
- `HYPOTHESIS` — Likely correct but needs verification (50-79%)
- `NEEDS_INFO` — Cannot determine without more data (<50%)

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/analyze/jira/{key}` | POST | Investigate a Jira ticket (full pipeline) |
| `/api/analyze` | POST | Investigate with custom input |
| `/api/jira/{key}` | GET | Fetch raw Jira ticket |
| `/api/jira/poll` | POST | Search Jira with JQL |
| `/api/confluence/search` | GET | Search Confluence docs |
| `/api/logs/search` | GET | Search server logs |
| `/api/knowledge/search` | GET | Search knowledge store |
| `/api/knowledge/documents` | POST | Store document |
| `/api/knowledge/similar` | GET | Find similar documents |
| `/api/incidents` | POST/GET | Manage incidents |
| `/actuator/health` | GET | Health check |

## Context Sources

AIRA gathers context from multiple sources before forming a hypothesis:

| Source | What it provides | Config |
|--------|-----------------|--------|
| Knowledge Base | Similar resolved incidents from same project/type | Always active |
| Confluence | Related runbooks, troubleshooting guides, TSD docs | `--confluence.api-token=TOKEN` |
| Server Logs | Recent ERROR/exception lines from log files | `--logs.base-path=/path/to/logs` |
| Source Code | Code context around stack trace references | `--sourcecode.repo-paths=/path/to/src` |
| Jira Comments | Existing engineer investigation notes (RCA detection) | Automatic |
| Attachments | CSV/text file content, image OCR (when vision model available) | Automatic |

## Performance

| Metric | Result |
|--------|--------|
| Tokens per investigation | ~170 (vs 3000+ raw) |
| Token reduction | **94%** |
| Investigation response time | 12-15 seconds (with Jira similarity search) |
| Investigation without Jira search | 6-8 seconds |
| Context gathering | <2 seconds |

## Tech Stack

- Java 17 + Spring Boot 3.3.5
- PostgreSQL 17 + pgvector (vector similarity search)
- Flyway (database migrations)
- Ollama (qwen3:1.7b for investigation, nomic-embed-text for embeddings, llava:7b for OCR)
- Lombok 1.18.32
- Maven (forked JDK 17 compilation)
- JUnit 5 + Mockito + AssertJ (21 tests)

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_URL` | http://localhost:11434 | Ollama endpoint |
| `OLLAMA_MODEL` | qwen3:1.7b | LLM model for investigation |
| `OLLAMA_EMBED_MODEL` | nomic-embed-text | Embedding model |
| `OLLAMA_VISION_MODEL` | llava:7b | Vision model for image OCR |
| `JIRA_BASE_URL` | https://ctosrepo.atlassian.net | Jira instance |
| `JIRA_USERNAME` | faiz@ctos.com.my | Jira username |
| `JIRA_API_TOKEN` | (required) | Atlassian API token |
| `CONFLUENCE_BASE_URL` | https://ctosrepo.atlassian.net/wiki | Confluence instance |
| `CONFLUENCE_API_TOKEN` | (same as Jira) | Confluence API token |
| `CONFLUENCE_SPACE_KEY` | (optional) | Filter to specific space |
| `LOGS_BASE_PATH` | (optional) | Local log directory path |
| `SOURCE_REPO_PATHS` | (optional) | Comma-separated local repo paths |

## Roadmap

| Phase | Status | Feature |
|-------|--------|---------|
| 1 | ✅ | Incident management, Jira integration, Document Intelligence |
| 2 | ✅ | Knowledge Store + RAG, Investigation Engine |
| 3 | ✅ | Confluence integration, Log reader, Source code context |
| 4 | ✅ | Attachment OCR, RCA comment detection, Investigation mode |
| 5 | 📋 | pgvector embeddings, vector similarity search |
| 6 | 📋 | Pre-indexed resolved tickets (background job) |
| 7 | 📋 | Agent domain (autonomous investigation workflows) |
| 8 | 📋 | Reporting (MTTR, trends, KPIs) |
| 9 | 📋 | SSH log collection from remote servers |
