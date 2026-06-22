# AIRA — Automated Investigation and Response Algorithm

## Demo Presentation

---

## 1. What is AIRA?

An **autonomous engineering investigation platform** that:

- Reads Jira incident tickets automatically
- Gathers context from multiple sources (Confluence, logs, source code)
- Produces an **investigation report** — not a guess
- Tells you what it **knows**, what it **doesn't know**, and what you need to **find out**

> AIRA is NOT an RCA tool.
> It is an investigation assistant that asks the right questions.

---

## 2. The Problem

### How engineers investigate today:

```
1. Open Jira ticket               → 2 mins
2. Read description + comments    → 5 mins
3. Search Confluence for runbook  → 5 mins
4. SSH to server, grep logs       → 10 mins
5. Find the source code           → 5 mins
6. Correlate with past incidents  → 10 mins
7. Write RCA                      → 15 mins
                                  ─────────
                                   ~52 mins per incident
```

### Pain points:
- 🔴 Manual context gathering across 5+ systems
- 🔴 No correlation with historical incidents
- 🔴 Each engineer starts from scratch
- 🔴 Knowledge lost when engineers leave
- 🔴 RCA quality varies by individual

---

## 3. How AIRA Solves It

```
Engineer clicks "Investigate" on a Jira ticket
              ↓
    AIRA gathers ALL context automatically
              ↓
    Produces investigation report in ~12 seconds
              ↓
    Engineer verifies and acts (not starts from zero)
```

**Time saved: 52 mins → 12 seconds for initial investigation**

---

## 4. Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                    AIRA Platform (Spring Boot)                   │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────────┐ │
│  │ Incident │  │  Document    │  │     Knowledge Store       │ │
│  │ Domain   │  │ Intelligence │  │  (PostgreSQL + pgvector)   │ │
│  │          │  │              │  │                             │ │
│  │ • CRUD   │  │ • Parse Jira │  │ • Full-text search         │ │
│  │ • Status │  │ • Extract    │  │ • Metadata filtering       │ │
│  │          │  │ • Normalize  │  │ • Vector similarity (future)│ │
│  └──────────┘  │ • OCR images │  │ • Ranked retrieval         │ │
│                 └──────────────┘  └───────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Integration Domain (Connectors)                │ │
│  │                                                              │ │
│  │  Jira ──── Confluence ──── Logs ──── Source Code            │ │
│  │  (API)     (API)          (Local)    (Local Repo)           │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Intelligence Domain (Investigation)            │ │
│  │                                                              │ │
│  │  Context Gathering → Prompt Builder → Ollama LLM → Parser  │ │
│  │                                                              │ │
│  │  Output: InvestigationResult (hypothesis + missing info)    │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
├────────────────────────────────────────────────────────────────┤
│  Infrastructure: PostgreSQL + pgvector │ Ollama LLM │ Flyway    │
└────────────────────────────────────────────────────────────────┘
```

---

## 5. Investigation Flow (Step by Step)

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Jira      │     │    Document     │     │   Knowledge      │
│   Ticket    │────▶│  Intelligence   │────▶│     Store        │
│   CM-5553   │     │                 │     │   (PostgreSQL)   │
└─────────────┘     │ • Parse text    │     └──────────────────┘
                    │ • Extract meta  │              │
                    │ • Read CSV/OCR  │              │
                    └─────────────────┘              │
                                                     ▼
┌─────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  Confluence │     │   RAG Context   │◀────│  Similar Tickets │
│   (Docs)    │────▶│   Gathering     │     │  (full-text      │
└─────────────┘     │                 │     │   search)        │
                    │  Combine all    │     └──────────────────┘
┌─────────────┐     │  sources into   │
│ Server Logs │────▶│  compact prompt │
└─────────────┘     │  (~170 tokens)  │
                    │                 │
┌─────────────┐     └────────┬────────┘
│ Source Code │────▶          │
└─────────────┘              ▼
                    ┌─────────────────┐
                    │   Ollama LLM    │
                    │  (qwen3:1.7b)   │
                    │                 │
                    │ "Do NOT guess.  │
                    │  Say what's     │
                    │  MISSING."      │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  Investigation  │
                    │    Report       │
                    │                 │
                    │ • Hypothesis    │
                    │ • Evidence      │
                    │ • Missing Info  │
                    │ • Questions     │
                    │ • Next Steps    │
                    └─────────────────┘
```

---

## 6. RAG — What Gets Sent to LLM

### ❌ WRONG approach (without RAG):

```
Send entire Jira ticket (3000+ tokens)
├── Full ADF markup with formatting
├── All comments ("any update?", email threads)
├── Base64 image references
├── User mentions, timestamps, links
└── Result: LLM hallucinates a "root cause"
```

### ✅ AIRA approach (with RAG):

```
Send only relevant context (~170 tokens)
├── Incident: service=cm, summary="eTR batch failed"
├── Source Code: [file.java:87] the actual failing line
├── Logs: FileNotFoundException at 20:41:03
├── Confluence: "eTR batch uses relative path for image..."
├── Similar: CM-5309 "Save failed" (resolved)
└── Result: Hypothesis with evidence + missing info
```

**94% token reduction. Better results.**

---

## 7. Investigation Output Example

For ticket **CM-5553** (eTR batch upload failed):

```json
{
  "status": "NEEDS_INFO",
  "hypothesis": "Batch processing failure possibly related to file path
                 resolution or configuration change during deployment",
  "confidencePercent": 45,
  "evidenceFound": [
    "Batch upload error mentioned in ticket",
    "Customer not updated error",
    "Confluence docs reference eTR batch path handling"
  ],
  "missingInfo": [
    "Server logs with stack trace from 15/06/2026 20:41",
    "Deployment history — was there a release before this?",
    "Batch processor working directory configuration"
  ],
  "questionsToAsk": [
    "Was there a deployment or config change on 15/06/2026?",
    "Is this still occurring or has it self-recovered?",
    "Can you provide the IndCustBatchAgingBatchAndEtrProcessor logs?"
  ],
  "nextSteps": [
    "Collect server logs from 15/06/2026 20:00-21:00",
    "Check deployment history for that date",
    "Verify batch processor file path configuration"
  ]
}
```

**Compare to actual root cause:**
> Deployment changed working directory → relative path `../../images/photo_upload_image.gif` broke → `FileNotFoundException`

AIRA correctly identified: deployment-related, file path issue, batch processor config — and asked exactly the right questions to confirm it.

---

## 8. Demo — Live Walkthrough

### Step 1: Open UI
```
http://localhost:8080
```

### Step 2: Enter Jira ticket
```
CM-5553
```

### Step 3: Click "Analyze Ticket"

### What happens behind the scenes:
1. Fetches CM-5553 from Jira (API v3)
2. Downloads CSV attachments
3. Parses description + comments
4. Searches Jira for similar resolved Bug tickets
5. Searches Confluence for "eTR batch" documentation
6. Checks local logs (if configured)
7. Looks up source code references (if configured)
8. Builds compact prompt (~170 tokens)
9. Calls Ollama for investigation
10. Returns structured report

### Step 4: Review Investigation Report
- Status badge (NEEDS_INFO / HYPOTHESIS / CONFIRMED)
- Hypothesis section
- Missing information (highlighted in yellow)
- Questions to ask
- Next investigation steps
- Recommendations (if hypothesis confirmed)

---

## 9. Key Metrics

| Metric | Value |
|--------|-------|
| Token usage per investigation | ~170 tokens |
| Token reduction vs raw | **94%** |
| Investigation time | 12-15 seconds |
| Context sources | 6 (Knowledge, Jira, Confluence, Logs, Source, Comments) |
| Tests | 21 passing |
| LLM model | qwen3:1.7b (1.7B params, runs locally) |
| Database | PostgreSQL + pgvector |
| Zero cloud AI cost | Ollama runs on-premise |

---

## 10. What Makes AIRA Different

| Traditional Approach | AIRA |
|---------------------|------|
| Engineer manually gathers context | Automatic multi-source gathering |
| Each investigation starts from zero | Builds on accumulated knowledge |
| RCA quality depends on individual | Consistent investigation framework |
| Knowledge lost when people leave | Knowledge stored in PostgreSQL |
| No correlation with past incidents | Automatic similarity matching |
| Guesses root cause without evidence | Says "I don't know, here's what I need" |
| Full Jira dump to ChatGPT (3000+ tokens) | Focused RAG context (170 tokens) |

---

## 11. Technology Stack

| Layer | Technology |
|-------|-----------|
| Application | Java 17 + Spring Boot 3.3.5 |
| Database | PostgreSQL 17 + pgvector |
| Migrations | Flyway |
| AI/LLM | Ollama (local, no cloud cost) |
| Models | qwen3:1.7b (RCA), nomic-embed-text (embeddings) |
| Search | Full-text (tsvector) + metadata + vector (future) |
| Build | Maven (forked JDK 17) |
| Test | JUnit 5 + Mockito + AssertJ |
| UI | Single-page HTML (built-in) |

---

## 12. Current Integrations

| Source | Status | What it provides |
|--------|--------|-----------------|
| Jira | ✅ Live | Fetch tickets, search similar resolved, attachments |
| Confluence | ✅ Live | Search related runbooks/TSD docs |
| Server Logs | ✅ Ready | Grep local log folders (needs path config) |
| Source Code | ✅ Ready | Read local repos, extract code around stack traces |
| Attachments | ✅ Live | CSV/text extraction, OCR (needs vision model) |
| RCA Detection | ✅ Live | Detect existing engineer RCA in comments |

---

## 13. Goal

### Short-term (Current):
- **Reduce initial investigation time** from 52 mins to 12 seconds
- **Identify what's missing** rather than guess wrongly
- **Accumulate engineering knowledge** that persists across team changes
- **Zero cloud AI cost** — everything runs on-premise via Ollama

### Medium-term:
- Pre-index all resolved tickets (background job, no live Jira search)
- pgvector embeddings for semantic similarity (not just keyword match)
- SSH to production servers for real-time log collection
- Pattern detection across incidents (recurring issues)

### Long-term:
- **Bug prediction** — identify at-risk components before they fail
- **Architecture recommendations** — based on incident patterns
- **Autonomous agents** — that investigate without human prompting
- **Engineering Intelligence Copilot** — integrated into developer workflow

---

## 14. Future Roadmap

```
                        NOW                    NEXT                  FUTURE
                    ┌──────────┐          ┌──────────┐         ┌──────────┐
                    │ Phase 1-4│          │ Phase 5-6│         │ Phase 7+ │
                    │ COMPLETE │          │ PLANNED  │         │ VISION   │
                    └──────────┘          └──────────┘         └──────────┘

Investigation ──────────────────────────────────────────────────────────────▶
    ✅ Multi-source RAG           Pre-indexed tickets        Autonomous agents
    ✅ Hypothesis mode            Vector similarity          Self-improving
    ✅ Confluence docs            SSH log collection         Bug prediction
    ✅ Source code context        Pattern detection          Architecture advisor
    ✅ Attachment OCR             Scheduled polling          Smart dashboards
```

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Jira integration + Document Intelligence | ✅ Done |
| 2 | Knowledge Store + RAG + Investigation Engine | ✅ Done |
| 3 | Confluence + Logs + Source Code context | ✅ Done |
| 4 | Investigation mode (hypothesis, not RCA) | ✅ Done |
| 5 | pgvector embeddings + vector similarity | 📋 Next |
| 6 | Pre-indexed resolved tickets (background) | 📋 Next |
| 7 | SSH log collection from remote servers | 📋 Planned |
| 8 | Agent domain (autonomous workflows) | 📋 Planned |
| 9 | Reporting (MTTR, trends, KPIs) | 📋 Planned |
| 10 | Bug prediction + Architecture recommendations | 🔮 Vision |

---

## 15. Summary

```
┌────────────────────────────────────────────────┐
│                                                  │
│   AIRA = Investigation Assistant                 │
│                                                  │
│   • Gathers context (you don't have to)         │
│   • Forms hypothesis (clearly labeled)          │
│   • Tells you what's missing                    │
│   • Asks the right questions                    │
│   • Suggests next steps                         │
│   • Learns from every resolved ticket           │
│                                                  │
│   Not a replacement for engineers.              │
│   A force multiplier for investigation.         │
│                                                  │
└────────────────────────────────────────────────┘
```

---

## Appendix: How to Run

```bash
# Prerequisites
brew services start postgresql@17
brew services start ollama
ollama pull qwen3:1.7b

# Run
chmod +x run.sh
./run.sh

# Open
http://localhost:8080
```
