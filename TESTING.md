# Manual Testing Guide

## Prerequisites

Ensure these are running:
```bash
brew services start postgresql@17
brew services start redis
brew services start ollama
```

Verify:
```bash
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
psql -d engineering_intelligence -c "SELECT 1;"   # Should return 1
redis-cli ping                                     # Should return PONG
curl -s http://localhost:11434/api/version          # Should return version JSON
```

## Step 1: Start All Services

Open 7 terminal tabs. Run each from `platform/` directory:

```bash
# Tab 1: Knowledge Store
cd /Users/faizfarhan/POC/Incident-analyzer/platform/knowledge-store-service
mvn spring-boot:run

# Tab 2: Canonical Transformer
cd /Users/faizfarhan/POC/Incident-analyzer/platform/canonical-transformer-service
mvn spring-boot:run

# Tab 3: Similarity Service
cd /Users/faizfarhan/POC/Incident-analyzer/platform/similarity-service
mvn spring-boot:run

# Tab 4: Incident Analysis
cd /Users/faizfarhan/POC/Incident-analyzer/platform/incident-analysis-service
mvn spring-boot:run

# Tab 5: Incident Ingestion
cd /Users/faizfarhan/POC/Incident-analyzer/platform/incident-ingestion-service
mvn spring-boot:run

# Tab 6: Failure Simulator
cd /Users/faizfarhan/POC/Incident-analyzer/platform/failure-simulator-service
mvn spring-boot:run

# Tab 7: API Gateway
cd /Users/faizfarhan/POC/Incident-analyzer/platform/api-gateway
mvn spring-boot:run
```

Wait ~30 seconds for all services to start.

## Step 2: Verify All Services Are Up

```bash
# Check each service health
curl -s http://localhost:8084/actuator/health  # Knowledge Store
curl -s http://localhost:8082/health           # Transformer
curl -s http://localhost:8085/health           # Similarity
curl -s http://localhost:8083/health           # Analysis
curl -s http://localhost:8081/health           # Ingestion
curl -s http://localhost:8086/health           # Simulator
curl -s http://localhost:8080/api/health       # Gateway (aggregated)
```

Expected: All return `{"status":"UP"}` or similar.

---

## Test Case 1: Direct Transformer (bypass ingestion)

Tests log parsing and event normalization.

```bash
curl -X POST http://localhost:8082/api/transform \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "payment-service",
    "description": "2026-06-12T10:30:45.123 ERROR [payment-service] java.net.SocketTimeoutException: Connect timed out\n    at java.net.Socket.connect(Socket.java:600)\n2026-06-12T10:30:46.456 ERROR [payment-service] com.zaxxer.hikari.pool.HikariPool: Connection not available\n2026-06-12T10:30:47.789 ERROR [payment-service] java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available",
    "source": "MANUAL"
  }' | python3 -m json.tool
```

**Expected:** JSON with:
- `incidentId` (UUID)
- `severity`: "MEDIUM" or "LOW"
- `exceptionCounts`: SocketTimeoutException, SQLTransientConnectionException
- `errorDistribution`: percentages summing to 100
- `events`: 3 entries (2 EXCEPTION + 1 STACK_TRACE)

---

## Test Case 2: Submit Incident via Ingestion (full pipeline without AI)

Tests ingestion → transformer → Redis publish → knowledge store.

```bash
curl -X POST http://localhost:8081/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "order-service",
    "description": "2026-06-15T14:00:01.001 ERROR [order-service] NullPointerException: Cannot invoke method on null object\n    at com.order.OrderController.createOrder(OrderController.java:45)\n    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:97)\n2026-06-15T14:00:02.002 ERROR [order-service] NullPointerException: Cannot invoke method on null object\n    at com.order.OrderController.createOrder(OrderController.java:45)\n2026-06-15T14:00:03.003 WARN [order-service] OrderService: Retry attempt 3 failed",
    "logSnippets": [
      "2026-06-15T14:00:04.004 ERROR [order-service] OrderProcessingException: Failed to process order ORD-12345"
    ],
    "source": "MANUAL",
    "metadata": {"environment": "production", "cluster": "my-east-1"}
  }'
```

**Expected:** `202 Accepted` with `incidentId`.

Then verify it was stored:
```bash
curl -s http://localhost:8084/api/knowledge/incidents | python3 -m json.tool
```

**Expected:** List with 1 incident, showing serviceName "order-service".

---

## Test Case 3: Run Failure Simulation

Tests simulator → ingestion → transformer → knowledge store.

```bash
curl -X POST http://localhost:8086/api/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "scenario": "DATABASE_UNAVAILABLE",
    "targetService": "payment-service",
    "logLineCount": 30
  }' | python3 -m json.tool
```

**Expected:** SimulationResult with `simulationId`, `incidentId`, `generatedLogLines: 30`.

Try other scenarios:
```bash
# Service timeout
curl -X POST http://localhost:8086/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"scenario": "SERVICE_TIMEOUT", "targetService": "auth-service", "logLineCount": 20}'

# Null pointer
curl -X POST http://localhost:8086/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"scenario": "NULL_POINTER_EXCEPTION", "targetService": "user-service", "logLineCount": 15}'
```

List all scenarios:
```bash
curl -s http://localhost:8086/api/simulate/scenarios | python3 -m json.tool
```

---

## Test Case 4: Full Analysis Pipeline (requires Ollama with model)

Tests the complete flow: ingestion → transform → similarity → AI → persist → output.

**Prerequisite:** Ollama must have a model pulled:
```bash
ollama pull qwen3:1.7b
```

Submit incident through the gateway:
```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "ccris-gateway",
    "description": "2026-06-15T09:00:01.123 ERROR [ccris-gateway] java.net.ConnectException: Connection refused (Connection refused)\n    at java.net.PlainSocketImpl.socketConnect(Native Method)\n2026-06-15T09:00:02.456 ERROR [ccris-gateway] java.net.SocketTimeoutException: Read timed out\n2026-06-15T09:00:03.789 ERROR [ccris-gateway] java.net.SocketTimeoutException: Read timed out\n2026-06-15T09:00:04.012 ERROR [ccris-gateway] java.net.SocketTimeoutException: Read timed out\n2026-06-15T09:00:05.345 WARN [ccris-gateway] CircuitBreaker: OPEN for downstream-ccris\n2026-06-15T09:00:06.678 ERROR [ccris-gateway] com.ctos.gateway.CcrisServiceException: CCRIS service unavailable after 3 retries",
    "source": "MANUAL",
    "metadata": {"severity_hint": "HIGH"}
  }'
```

Wait 1-2 minutes for AI analysis, then check:

```bash
# Check knowledge store for the analysis
curl -s http://localhost:8084/api/knowledge/incidents | python3 -m json.tool

# Check generated output files
ls -la output/confluence/rca/
ls -la output/jira/comments/
cat output/confluence/rca/*.md    # View generated RCA document
cat output/jira/comments/*.md     # View generated Jira comment
cat output/index.json             # View manifest
```

**Expected outputs:**
- `output/confluence/rca/2026-06-16_ccris-gateway_*.md` — Full RCA document
- `output/jira/comments/2026-06-16_ccris-gateway_rca-comment.md` — Jira comment
- `output/jira/incidents/2026-06-16_ccris-gateway_incident.json` — Ticket payload

---

## Test Case 5: Similarity Search

After running Test Cases 2-4 (multiple incidents stored), test similarity:

```bash
# Get an incident ID from the knowledge store
INCIDENT_ID=$(curl -s http://localhost:8084/api/knowledge/incidents | python3 -c "import json,sys; data=json.load(sys.stdin); print(data['content'][0]['incidentId'])" 2>/dev/null)

echo "Testing similarity for: $INCIDENT_ID"

curl -X POST http://localhost:8085/api/similarity/search \
  -H "Content-Type: application/json" \
  -d "{
    \"incidentId\": \"$INCIDENT_ID\",
    \"service\": \"payment-service\",
    \"exceptionTypes\": [\"SocketTimeoutException\", \"ConnectionException\"],
    \"exceptionCounts\": {\"SocketTimeoutException\": 3, \"ConnectionException\": 1},
    \"errorDistribution\": {\"SocketTimeoutException\": 75.0, \"ConnectionException\": 25.0},
    \"maxResults\": 5
  }" | python3 -m json.tool
```

**Expected:** List of similar incidents with scores >= 0.3 (or empty if only 1 incident stored).

---

## Test Case 6: Validation Errors

```bash
# Missing serviceName — should return 400
curl -X POST http://localhost:8081/api/incidents \
  -H "Content-Type: application/json" \
  -d '{"description": "Some error occurred"}'

# Missing description — should return 400
curl -X POST http://localhost:8081/api/incidents \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "my-service"}'

# Empty body — should return 400
curl -X POST http://localhost:8081/api/incidents \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Expected:** HTTP 400 with validation error messages.

---

## Test Case 7: Gateway Routing

```bash
# These should all route correctly through gateway (port 8080)
curl -s http://localhost:8080/api/knowledge/incidents | head -1   # → knowledge-store
curl -s http://localhost:8080/api/simulate/scenarios | head -1     # → simulator
curl -s http://localhost:8080/api/health                           # → aggregated health
```

---

## Test Case 8: Graceful Degradation

**Without Ollama (stop it):**
```bash
brew services stop ollama
```

Submit an incident — it should still be accepted and stored, analysis marked as FAILED:
```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "test-service",
    "description": "2026-06-15T10:00:00.000 ERROR [test-service] TestException: This is a test",
    "source": "MANUAL"
  }'
```

Check the analysis status:
```bash
curl -s http://localhost:8084/api/knowledge/incidents | python3 -m json.tool
# Latest incident should show — analysis may be FAILED if AI was involved
```

Restart Ollama after:
```bash
brew services start ollama
```

---

## Cleanup

Stop all services (Ctrl+C in each terminal tab), then:

```bash
# Stop infrastructure
brew services stop redis
brew services stop ollama
# Keep PostgreSQL running if you want to preserve data

# Or drop the database to start fresh
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
dropdb engineering_intelligence
createdb engineering_intelligence
# Next startup will re-run Flyway migrations
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Port already in use | `lsof -i :8084` then `kill <PID>` |
| Knowledge Store fails to start | Check PostgreSQL: `brew services restart postgresql@17` |
| Transformer warns about Redis | Normal — works in sync mode without Redis |
| Analysis takes too long | Ollama model inference — wait up to 5 minutes for first run |
| No output files generated | Check `output/` directory exists in the working directory where analysis service runs |
| Maven compile fails | Ensure VPN connected for Artifactory access |
