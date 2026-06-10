# AI Log Analyzer

A minimal Java Spring Boot application that parses `.log` files for Java exceptions, groups and counts them, then sends a summary to Ollama (qwen3:8b) for AI-powered root cause analysis.

## Features

- Upload `.log` files through a clean web UI
- Extracts ERROR lines, exception names, and stack trace root causes
- Groups identical exceptions and counts occurrences
- Sends a structured summary to Ollama for analysis
- Displays AI-generated root cause, business impact, and recommended actions

## Prerequisites

- Java 17+
- Maven 3.8+
- [Ollama](https://ollama.com/) installed and running locally

## Setup

### 1. Build the project

```bash
C:\Users\faiz\Downloads\apache-maven-3.2.5-bin\apache-maven-3.2.5\bin\mvn.bat clean package -s settings-override.xml -DskipTests
```

Or if you have Maven on PATH:

```bash
mvn clean package -DskipTests
```

### 2. Install and start Ollama

Download Ollama from https://ollama.com/ and install it.

Pull the required model:

```bash
ollama pull qwen3:8b
```

Make sure Ollama is running (it starts automatically after install, or run `ollama serve`).

### 3. Run the application

```bash
mvn spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/ai-log-analyzer-1.0.0.jar
```

Open your browser at **http://localhost:8080**

## Usage

1. Open the web page at `http://localhost:8080`
2. Upload a `.log` file (Java application logs work best)
3. View the exception counts and AI analysis

## Project Structure

```
log-analyzer/
├── src/main/java/com/loganalyzer/
│   ├── LogAnalyzerApplication.java      # Spring Boot entry point
│   ├── controller/
│   │   └── LogController.java           # Web controller (upload + analyze)
│   ├── service/
│   │   ├── LogParserService.java        # Regex-based exception extraction
│   │   └── OllamaService.java          # Ollama REST API client
│   └── model/
│       └── AnalysisResult.java          # Result data holder
├── src/main/resources/
│   ├── application.properties           # Configuration
│   └── templates/
│       ├── index.html                   # Upload page (Thymeleaf)
│       └── results.html                 # Results display (Thymeleaf)
├── sample.log                           # Sample log for testing
├── pom.xml                              # Maven build file
└── README.md
```

## How It Works

1. **Parse**: The log parser uses regex to find fully-qualified Java exception names (e.g., `java.sql.SQLTransientConnectionException`) and standalone exception class names (e.g., `NullPointerException`).

2. **Count**: Identical exceptions are grouped and counted using a `HashMap`.

3. **Summarize**: A structured summary string is built from the counts.

4. **Analyze**: The summary is sent to Ollama with a prompt asking for root cause analysis, business impact, recommended actions, and confidence level.

5. **Display**: Results are rendered in the browser with Thymeleaf templates.

## Configuration

Edit `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `ollama.url` | `http://localhost:11434/api/generate` | Ollama API endpoint |
| `ollama.model` | `qwen3:8b` | LLM model to use |
| `ollama.timeout-seconds` | `120` | Request timeout |
| `server.port` | `8080` | Application port |

## Tech Stack

- Java 17
- Spring Boot 3.3
- Thymeleaf (server-side templates)
- WebClient (HTTP client for Ollama)
- Ollama REST API (no LangChain, no RAG, no agents)
