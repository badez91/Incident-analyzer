# AIRA — Testing Guide

## Prerequisites

```bash
brew services start postgresql@17
brew services start ollama
ollama pull qwen3:1.7b
```

Verify:
```bash
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
psql -d engineering_intelligence -c "SELECT 1;"
curl -s http://localhost:11434/api/version
```

## Unit Tests

```bash
# Run all 21 tests (offline — no VPN needed)
mvn test -o

# Expected output:
# Tests run: 5  — ResponseParserTest (investigation format parsing)
# Tests run: 1  — AiraApplicationTest (Spring context loads)
# Tests run: 10 — ContentExtractorTest (extraction logic)
# Tests run: 5  — IncidentServiceTest (service layer)
# Total: 21 tests, 0 failures
```

## Start Application

```bash
# Recommended: use run script
chmod +x run.sh
./run.sh

# Or manually:
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=./truststore.jks -Djavax.net.ssl.trustStorePassword=changeit" \
  -Dspring-boot.run.arguments="--jira.api-token=YOUR_TOKEN --confluence.api-token=YOUR_TOKEN"
```

## End-to-End Test Cases

### Test 1: Health Check

```bash
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Test 2: Investigate Jira Ticket

```bash
curl -s -X POST http://localhost:8080/api/analyze/jira/CM-5553 | python3 -m json.tool
```

Expected response contains:
- `status`: "NEEDS_INFO" or "HYPOTHESIS"
- `hypothesis`: A hypothesis clearly labeled as such
- `missingInfo`: List of what data is needed
- `questionsToAsk`: Specific questions for the team
- `nextSteps`: Investigation actions
- `confidencePercent`: Typically 30-70% without logs/source

### Test 3: Manual Investigation

```bash
curl -s -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "payment-service",
    "summary": "Timeout errors during batch processing, customer records not updated"
  }' | python3 -m json.tool
```

### Test 4: Confluence Search

```bash
curl -s 'http://localhost:8080/api/confluence/search?keywords=eTR,batch&maxResults=2' | python3 -m json.tool
# Expected: List of related Confluence pages (if configured)
```

### Test 5: Knowledge Base Search

```bash
curl -s 'http://localhost:8080/api/knowledge/search?service=cm&limit=5' | python3 -m json.tool
```

### Test 6: Create Incident

```bash
curl -s -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "payment-service", "severity": "HIGH", "summary": "Timeout errors"}' | python3 -m json.tool
```

### Test 7: Validation Error

```bash
curl -s -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{}' | python3 -m json.tool
# Expected: 400 with "serviceName is required"
```

### Test 8: Demo UI

Open http://localhost:8080 in browser:
- Enter ticket key (e.g., CM-5553) and click "Analyze Ticket"
- Should show Investigation Report with status, hypothesis, missing info, questions, next steps

## Integration Status Check

```bash
# Check Confluence connectivity
curl -s http://localhost:8080/api/confluence/status
# Expected: {"enabled":true} or {"enabled":false}

# Check log reader
curl -s http://localhost:8080/api/logs/status
# Expected: {"enabled":true} or {"enabled":false}
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| PKIX / SSL error | Missing `-Djavax.net.ssl.trustStore=./truststore.jks` in JVM args |
| Jira 404 | Key must be uppercase (CM-5553 not cm-5553) — auto-fixed now |
| Maven hangs | VPN may be blocking Artifactory; use `mvn ... -o` for offline mode |
| Port 8080 in use | `lsof -ti:8080 | xargs kill -9` |
| Confluence not found | Verify base-url includes `/wiki` path |
| Ollama timeout | Check `ollama serve` is running; increase `OLLAMA_TIMEOUT` |
