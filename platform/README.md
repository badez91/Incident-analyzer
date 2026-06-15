# Engineering Intelligence Platform

A microservice-based platform that analyzes incidents, stores engineering knowledge, correlates information across logs, and generates AI-powered Root Cause Analysis (RCA) recommendations.

## Architecture

```
                    ┌─────────────────────┐
                    │   API Gateway :8080 │
                    └──────────┬──────────┘
         ┌────────────┬───────┴───────┬────────────┬──────────┐
         │            │               │            │          │
   ┌─────▼─────┐ ┌───▼───┐ ┌────────▼───┐ ┌─────▼────┐ ┌───▼──────┐
   │ Ingestion │ │Knowl- │ │ Similarity │ │ Analysis │ │Simulator │
   │   :8081   │ │Store  │ │   :8085    │ │  :8083   │ │  :8086   │
   └─────┬─────┘ │:8084  │ └────────────┘ └─────┬────┘ └──────────┘
         │        └───────┘                      │
   ┌─────▼──────┐                          ┌────▼─────┐
   │Transformer │                          │  Ollama  │
   │   :8082    │                          │ :11434   │
   └────────────┘                          └──────────┘
```

## Services

| Service | Port | Purpose |
|---------|------|---------|
| api-gateway | 8080 | Single entry point, routes requests |
| incident-ingestion | 8081 | Receives and validates incident submissions |
| canonical-transformer | 8082 | Normalizes logs into canonical events |
| incident-analysis | 8083 | AI-powered RCA generation pipeline |
| knowledge-store | 8084 | PostgreSQL persistence for all knowledge |
| similarity-service | 8085 | Finds historically similar incidents |
| failure-simulator | 8086 | Generates realistic test incidents |

## Prerequisites

- Java 17+
- Maven 3.8+ (configured with CTOS Artifactory)
- PostgreSQL 15+
- Redis 7+ (optional — platform works without it in synchronous mode)
- Ollama (for AI analysis)

### macOS Installation

```bash
# PostgreSQL
brew install postgresql@17
brew services start postgresql@17

# Redis (optional but recommended)
brew install redis
brew services start redis

# Ollama
brew services start ollama
ollama pull qwen3:1.7b
```

## Setup

### 1. Create the database

```bash
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
createdb engineering_intelligence
```

The Knowledge Store service will automatically run Flyway migrations on first startup.

### 2. Build all services

```bash
cd platform
mvn clean package -DskipTests
```

### 3. Run locally (without Docker)

Start services in order — dependencies first:

```bash
# Terminal 1: Knowledge Store (needs PostgreSQL)
cd knowledge-store-service && mvn spring-boot:run

# Terminal 2: Canonical Transformer (needs Redis, works without)
cd canonical-transformer-service && mvn spring-boot:run

# Terminal 3: Similarity Service (needs Knowledge Store)
cd similarity-service && mvn spring-boot:run

# Terminal 4: Incident Analysis (needs Knowledge Store + Similarity + Ollama)
cd incident-analysis-service && mvn spring-boot:run

# Terminal 5: Incident Ingestion (needs Transformer)
cd incident-ingestion-service && mvn spring-boot:run

# Terminal 6: Failure Simulator (needs Ingestion)
cd failure-simulator-service && mvn spring-boot:run

# Terminal 7: API Gateway (needs all above)
cd api-gateway && mvn spring-boot:run
```

### Quick Start Script

```bash
#!/bin/bash
# start-local.sh — Run from platform/ directory

export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
brew services start postgresql@17
brew services start redis 2>/dev/null
brew services start ollama

sleep 3

echo "Starting Knowledge Store (8084)..."
(cd knowledge-store-service && mvn spring-boot:run -q) &
sleep 5

echo "Starting Canonical Transformer (8082)..."
(cd canonical-transformer-service && mvn spring-boot:run -q) &

echo "Starting Similarity Service (8085)..."
(cd similarity-service && mvn spring-boot:run -q) &
sleep 3

echo "Starting Incident Analysis (8083)..."
(cd incident-analysis-service && mvn spring-boot:run -q) &

echo "Starting Incident Ingestion (8081)..."
(cd incident-ingestion-service && mvn spring-boot:run -q) &

echo "Starting Failure Simulator (8086)..."
(cd failure-simulator-service && mvn spring-boot:run -q) &
sleep 3

echo "Starting API Gateway (8080)..."
(cd api-gateway && mvn spring-boot:run -q) &

echo ""
echo "✅ All services starting. Gateway at http://localhost:8080"
echo "Press Ctrl+C to stop all."
wait
```

## Usage

### Submit an incident

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "payment-service",
    "description": "2026-06-12T10:30:45.123 ERROR [payment-service] SocketTimeoutException: Connection timed out\n2026-06-12T10:30:46.456 ERROR [payment-service] ConnectionPoolExhaustedException: No available connections\n    at com.payment.PaymentController.processPayment(PaymentController.java:45)",
    "logSnippets": [
      "2026-06-12T10:31:00.789 ERROR [payment-service] PSQLException: Connection pool exhausted"
    ],
    "source": "MANUAL",
    "metadata": {"environment": "production", "region": "my-east-1"}
  }'
```

### Run a failure simulation

```bash
curl -X POST http://localhost:8080/api/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "scenario": "DATABASE_UNAVAILABLE",
    "targetService": "order-service",
    "logLineCount": 50
  }'
```

### List available simulation scenarios

```bash
curl http://localhost:8080/api/simulate/scenarios
```

### Query knowledge store

```bash
# List all incidents
curl http://localhost:8084/api/knowledge/incidents

# Get specific incident
curl http://localhost:8084/api/knowledge/incidents/{incident-id}
```

### Find similar incidents

```bash
curl -X POST http://localhost:8080/api/similarity/search \
  -H "Content-Type: application/json" \
  -d '{
    "incidentId": "uuid-here",
    "service": "payment-service",
    "exceptionTypes": ["SocketTimeoutException"],
    "exceptionCounts": {"SocketTimeoutException": 5},
    "errorDistribution": {"SocketTimeoutException": 100.0},
    "maxResults": 5
  }'
```

### Check platform health

```bash
curl http://localhost:8080/api/health
```

## Output Generation

The platform generates Jira/Confluence documents locally in `./output/`:

```
output/
├── jira/
│   ├── incidents/       # Ticket JSON payloads
│   └── comments/        # RCA comments (Markdown)
├── confluence/
│   ├── rca/             # RCA documents (Markdown)
│   ├── postmortem/      # Postmortem documents
│   └── investigation/   # In-progress summaries
└── index.json           # Manifest of all generated outputs
```

These files are generated automatically after each analysis. When ready to post to real Jira/Confluence, set:

```yaml
integration:
  jira:
    enabled: true
  confluence:
    enabled: true
```

## Configuration

All services use environment variables for configuration:

| Variable | Default | Service |
|----------|---------|---------|
| `KNOWLEDGE_STORE_URL` | http://localhost:8084 | similarity, analysis |
| `SIMILARITY_URL` | http://localhost:8085 | analysis |
| `TRANSFORMER_URL` | http://localhost:8082 | ingestion |
| `INGESTION_URL` | http://localhost:8081 | simulator |
| `OLLAMA_URL` | http://localhost:11434 | analysis |
| `OLLAMA_MODEL` | qwen3:1.7b | analysis |
| `OLLAMA_TIMEOUT` | 300 | analysis |
| `REDIS_HOST` | localhost | transformer |
| `REDIS_PORT` | 6379 | transformer |
| `OUTPUT_DIR` | ./output | analysis |
| `JIRA_ENABLED` | false | analysis |
| `CONFLUENCE_ENABLED` | false | analysis |

## Project Structure

```
platform/
├── pom.xml                          # Parent POM (multi-module)
├── eip-common/                      # Shared library (models, DTOs, enums)
├── knowledge-store-service/         # PostgreSQL persistence + REST API
├── canonical-transformer-service/   # Log parsing + event normalization
├── similarity-service/              # Weighted similarity scoring
├── incident-analysis-service/       # AI pipeline + output generation
├── incident-ingestion-service/      # Validation + routing
├── api-gateway/                     # Reverse proxy + health aggregation
├── failure-simulator-service/       # Test incident generation
└── README.md
```

## Tech Stack

- Java 17 + Spring Boot 3.3
- PostgreSQL 17 (knowledge persistence)
- Redis 7 (event bus — optional)
- Ollama (local LLM inference)
- Flyway (database migrations)
- Jackson (JSON serialization)
- Maven (multi-module build)

## Without Redis

The platform runs fine without Redis — it just processes everything synchronously instead of event-driven. The Canonical Transformer logs a warning but still returns results. Install Redis when you want async processing.

## Without Ollama

If Ollama is unavailable or has no model pulled, the analysis service will:
- Retry 3 times with exponential backoff
- Mark the analysis as FAILED
- Still persist the incident and exception data to the Knowledge Store
- Still generate output files (with "Analysis failed" content)
