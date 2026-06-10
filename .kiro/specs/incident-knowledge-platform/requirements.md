# Requirements Document

## Introduction

This document defines the requirements for evolving the existing AI Log Analyzer into an Incident Knowledge Platform. The platform introduces persistent storage of log analyses, historical incident comparison via similarity matching, and context-enriched LLM analysis. It preserves the existing upload-and-analyze workflow while adding REST API endpoints for querying historical knowledge. The system uses PostgreSQL for storage, deterministic similarity scoring (no vector databases), and Ollama for AI-powered analysis.

## Glossary

- **Platform**: The Incident Knowledge Platform application, a Spring Boot service that analyzes log files, persists structured incident data, and provides historical incident knowledge.
- **CanonicalEvent**: A normalized representation of a single log line with consistent schema including timestamp, level, service, eventType, and message.
- **CanonicalEventTransformer**: The service component responsible for transforming raw log text into a list of CanonicalEvent records.
- **IncidentAnalysis**: A structured record representing a complete analysis of a log file, including error summary, exception counts, LLM-generated root cause, and metadata for similarity matching.
- **IncidentAnalysisService**: The service component that orchestrates the full analysis pipeline from event transformation through LLM analysis to persistence.
- **SimilarityMatchingService**: The service component that identifies historically similar incidents using weighted multi-factor scoring without vector databases.
- **KnowledgeStore**: The PostgreSQL-backed persistence layer that stores incident analyses and canonical events.
- **KnowledgeStoreRepository**: The data access component providing CRUD and query operations for the KnowledgeStore.
- **ScoredMatch**: A record representing a historical incident with its computed similarity score and match reasoning.
- **SimilarityScore**: A floating-point value in the range [0.0, 1.0] representing how similar two incidents are, computed from weighted factors.
- **JaccardIndex**: A set similarity measure computed as |intersection| / |union|, used for exception type overlap.
- **CosineSimilarity**: A vector similarity measure used for comparing error distribution percentages between incidents.
- **IncidentController**: The REST controller exposing endpoints for querying historical incident knowledge.
- **EnhancedPrompt**: An LLM prompt that includes both the current log summary and historical context from similar past incidents.

## Requirements

### Requirement 1: Canonical Event Transformation

**User Story:** As a platform operator, I want raw log files transformed into normalized canonical events, so that log data has a consistent structure for analysis and storage.

#### Acceptance Criteria

1. WHEN raw log content is provided, THE CanonicalEventTransformer SHALL parse each line that contains at least one non-whitespace character into a CanonicalEvent with timestamp, level, service, eventType, and message fields.
2. WHEN a log line contains a timestamp matching any of the formats "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-ddTHH:mm:ss.SSS", "yyyy/MM/dd HH:mm:ss", or "dd/MMM/yyyy:HH:mm:ss Z", THE CanonicalEventTransformer SHALL extract and normalize the timestamp to ISO-8601 format (yyyy-MM-dd'T'HH:mm:ss.SSSZ).
3. WHEN a log line does not contain a timestamp matching any of the recognized formats, THE CanonicalEventTransformer SHALL assign the current processing time in ISO-8601 format as the event timestamp.
4. WHEN a log line contains a level token matching one of ERROR, WARN, WARNING, INFO, DEBUG, or TRACE (case-insensitive), THE CanonicalEventTransformer SHALL assign the corresponding level value from the set: ERROR, WARN, INFO, DEBUG, or TRACE (mapping WARNING to WARN).
5. IF a log line does not contain a recognizable level token, THEN THE CanonicalEventTransformer SHALL assign INFO as the default level.
6. WHEN a log line contains a fully-qualified class name or a bracketed service identifier, THE CanonicalEventTransformer SHALL extract that value as the service field.
7. IF a log line does not contain an identifiable service name, THEN THE CanonicalEventTransformer SHALL assign "UNKNOWN" as the service field value.
8. THE CanonicalEventTransformer SHALL classify each event into one of the following eventType values based on the line content: EXCEPTION when a Java exception class name is present, ERROR when the level is ERROR and no exception class is detected, WARNING when the level is WARN, STACK_TRACE when the line begins with whitespace followed by "at " indicating a stack frame, or INFO for all other lines.
9. WHEN an event message exceeds 4000 bytes in UTF-8 encoding, THE CanonicalEventTransformer SHALL truncate the message to 4000 bytes at a valid character boundary.
10. THE CanonicalEventTransformer SHALL preserve the ordering of events as they appear in the input log content.

### Requirement 2: Incident Analysis Orchestration

**User Story:** As a platform operator, I want log analyses orchestrated end-to-end through a structured pipeline, so that each uploaded log produces a complete incident record enriched with historical context.

#### Acceptance Criteria

1. WHEN a log file is submitted for analysis, THE IncidentAnalysisService SHALL execute the pipeline steps in sequence: parse exceptions, transform to canonical events, find similar incidents, build enhanced prompt, call LLM, and persist the resulting incident record.
2. WHEN the analysis pipeline completes, THE IncidentAnalysisService SHALL generate a UUID v4 identifier for the incident analysis record that is unique across all stored records.
3. WHEN the analysis pipeline completes, THE IncidentAnalysisService SHALL record the UTC timestamp at which the analysis completed as the analysis date.
4. WHEN canonical events are produced, THE IncidentAnalysisService SHALL derive the errorDistribution as a percentage per exception type where all percentages sum to between 99% and 101% inclusive (rounding tolerance).
5. WHEN canonical events are produced, THE IncidentAnalysisService SHALL derive exceptionTypes as the set of keys from exceptionCounts.
6. WHEN the same log file content is analyzed multiple times, THE IncidentAnalysisService SHALL create a distinct incident record with a new UUID and a new timestamp for each analysis invocation.
7. IF the LLM call fails or times out during the pipeline, THEN THE IncidentAnalysisService SHALL persist the incident record with all fields populated up to the point of failure, mark the analysis status as incomplete, and include an error indication describing the failure reason.
8. IF the submitted log file contains no parseable exceptions, THEN THE IncidentAnalysisService SHALL not create an incident record and SHALL return an error indication stating that no exceptions were found in the log.

### Requirement 3: Similarity Matching

**User Story:** As a platform operator, I want to find historically similar incidents, so that patterns and recurring issues can be identified without manual searching.

#### Acceptance Criteria

1. WHEN an incident is analyzed, THE SimilarityMatchingService SHALL compute a similarity score against historical incidents using weighted factors: service match (30%), exception type overlap (35%), event category overlap (15%), and error distribution similarity (20%).
2. THE SimilarityMatchingService SHALL return similarity scores bounded within the range [0.0, 1.0] for all incident pairs.
3. THE SimilarityMatchingService SHALL produce symmetric similarity scores such that score(A, B) equals score(B, A) for all incident pairs.
4. THE SimilarityMatchingService SHALL exclude the current incident from its own similarity results.
5. WHEN computing exception type overlap, THE SimilarityMatchingService SHALL use the Jaccard index: |intersection| / |union| of the two exception type sets.
6. WHEN both exception type sets are empty, THE SimilarityMatchingService SHALL return a Jaccard index of 1.0.
7. WHEN one exception type set is empty and the other is not, THE SimilarityMatchingService SHALL return a Jaccard index of 0.0.
8. WHEN computing error distribution similarity, THE SimilarityMatchingService SHALL use cosine similarity over the percentage vectors.
9. THE SimilarityMatchingService SHALL only return matches with a similarity score of 0.3 or greater.
10. THE SimilarityMatchingService SHALL return results sorted by similarity score in descending order.
11. WHEN querying candidates for similarity, THE KnowledgeStoreRepository SHALL pre-filter by matching service name OR overlapping exception types, limited to 50 most recent candidates.

### Requirement 4: Knowledge Store Persistence

**User Story:** As a platform operator, I want incident analyses and their canonical events persisted to PostgreSQL, so that organizational knowledge accumulates over time and supports historical queries.

#### Acceptance Criteria

1. WHEN an incident analysis is persisted, THE KnowledgeStoreRepository SHALL store the incident record with all fields: id (UUID), analysisDate, serviceName, timeRange, exceptionCounts, exceptionTypes, errorDistribution, rootCause, and llmAnalysis.
2. WHEN canonical events are persisted, THE KnowledgeStoreRepository SHALL associate each event with its parent incident via incident_id foreign key.
3. WHEN an incident is deleted, THE KnowledgeStore SHALL cascade the deletion to all associated canonical events.
4. THE KnowledgeStore SHALL enforce that every stored CanonicalEvent references a valid incident_id that exists in the incident_analyses table.
5. WHEN canonical events are persisted, THE KnowledgeStoreRepository SHALL insert events in batches of 1000 regardless of log file size.
6. THE KnowledgeStore SHALL maintain indexes on service name, analysis date, and exception types to support querying by the SimilarityMatchingService pre-filter and REST API pagination.
7. WHEN an incident analysis and its canonical events are persisted, THE KnowledgeStoreRepository SHALL execute the incident insert and all event batch inserts within a single database transaction, rolling back all changes if any batch fails. THE KnowledgeStoreRepository SHALL enforce that rollback occurs whenever a batch insert fails, regardless of the rollback operation's own success.

### Requirement 5: Context-Enriched LLM Analysis

**User Story:** As a platform operator, I want the LLM analysis enriched with context from similar past incidents, so that root cause identification improves over time as the knowledge base grows.

#### Acceptance Criteria

1. WHEN similar incidents exist with score >= 0.3, THE IncidentAnalysisService SHALL include historical context in the LLM prompt containing up to 5 similar incidents ordered by similarity score descending, with each entry showing the incident's service, root cause, similarity percentage, and match factors.
2. WHEN no similar incidents exist, THE IncidentAnalysisService SHALL send the base prompt without historical context, producing output identical to the pre-knowledge-platform analysis behavior.
3. WHEN building the enhanced prompt, THE IncidentAnalysisService SHALL cap the historical context section at 8000 characters by including complete incident entries in score-descending order and omitting any further incidents that would exceed the cap.
4. IF the LLM fails to respond within the configured timeout, THEN THE IncidentAnalysisService SHALL persist the incident with events and error summary but with null llmAnalysis, regardless of whether historical context was available for the prompt. THE system SHALL NOT retry the LLM call on timeout.
5. WHEN a similar incident has null rootCause, THE IncidentAnalysisService SHALL omit that incident from the historical context section of the enhanced prompt.

### Requirement 6: REST API for Historical Knowledge

**User Story:** As an API consumer, I want REST endpoints for querying historical incident knowledge, so that incident data can be accessed programmatically by other tools and dashboards.

#### Acceptance Criteria

1. WHEN a GET request is made to /incidents, THE IncidentController SHALL return a paginated list of incident summaries with page parameter defaulting to 0 and size parameter defaulting to 20, with size capped at a maximum of 100.
2. WHEN a GET request is made to /incidents/{id} with a valid UUID, THE IncidentController SHALL return the full incident analysis detail including incident ID, timestamp, severity, root cause, summary, and recommended actions.
3. WHEN a GET request is made to /incidents/{id} with a non-existent UUID, THE IncidentController SHALL return HTTP 404 status.
4. WHEN a GET request is made to /incidents/similar with a valid incidentId parameter, THE IncidentController SHALL return a list of similar incidents sorted by similarity score descending, where each entry includes the incident ID, summary, and numeric similarity score between 0.0 and 1.0.
5. WHEN a GET request is made to /incidents/similar with a non-existent incidentId, THE IncidentController SHALL return HTTP 404 status.
6. THE IncidentController SHALL accept an optional maxResults parameter for similarity queries, defaulting to 5 and capped at a maximum of 50.
7. IF a GET request to /incidents includes a page or size parameter that is non-numeric or less than 0, THEN THE IncidentController SHALL return HTTP 400 status with an error message indicating the invalid parameter.
8. IF a GET request to /incidents/similar is made without the required incidentId parameter, THEN THE IncidentController SHALL return HTTP 400 status with an error message indicating the missing parameter.

### Requirement 7: Existing Workflow Preservation

**User Story:** As an existing user, I want the upload-and-analyze workflow to continue functioning as before, so that the new knowledge platform capabilities do not disrupt my current usage.

#### Acceptance Criteria

1. WHEN a user uploads a .log file via the web UI, THE Platform SHALL validate the file (must have .log extension and be non-empty), parse exceptions from the log content, generate AI analysis via the configured LLM, and display the results page containing the exception counts table and the structured AI analysis within the configured timeout period. IF the file does not have a .log extension or is empty, THEN THE Platform SHALL reject the upload and return an error message indicating the specific validation failure.
2. WHEN the knowledge store (PostgreSQL) is unavailable during a log analysis request, THE Platform SHALL complete the log parsing and AI analysis steps without interruption, display the full results to the user, and log the persistence failure at ERROR level without surfacing the knowledge store error to the user.
3. WHEN the knowledge store is empty (cold start), THE Platform SHALL perform analysis using only the base prompt without historical context, producing results that contain the same structural fields (severity, root cause, business impact, recommended actions, and summary) as when historical context is available.
4. IF the knowledge platform introduces new dependencies that fail to initialize at startup, THEN THE Platform SHALL still start successfully and serve the log upload-and-analyze workflow without requiring the knowledge store to be reachable.

### Requirement 8: Data Model Validation

**User Story:** As a platform developer, I want strict validation on data models, so that invalid data cannot corrupt the knowledge store or produce incorrect similarity results.

#### Acceptance Criteria

1. THE Platform SHALL enforce that CanonicalEvent level is one of: ERROR, WARN, INFO, DEBUG, TRACE.
2. THE Platform SHALL enforce that CanonicalEvent eventType is one of: EXCEPTION, ERROR, WARNING, STACK_TRACE, INFO.
3. THE Platform SHALL enforce that ScoredMatch similarityScore is between 0.0 and 1.0 inclusive.
4. THE Platform SHALL enforce that IncidentAnalysis timeRange start is before or equal to timeRange end.
5. THE Platform SHALL enforce that each ScoredMatch contains at least one entry in matchReasons.
6. THE Platform SHALL enforce that errorDistribution percentages for an incident sum to within 99.9 and 100.1 inclusive (±0.1 tolerance for floating point).
7. THE Platform SHALL enforce that exceptionTypes equals the key set of exceptionCounts for any given incident.
8. IF any data model validation constraint is violated during incident persistence, THEN THE Platform SHALL reject the operation AND return an error message indicating which field failed validation and why. Both rejection and error message are required together. THE Platform SHALL NOT persist any partial data for that incident.
9. THE Platform SHALL enforce that CanonicalEvent timestamp, level, eventType, and message fields are non-null. The message field SHALL be a non-empty string (containing at least one character, including whitespace-only strings) of at most 4000 bytes in UTF-8 encoding.
10. THE Platform SHALL perform all data model validation before writing to the KnowledgeStore, ensuring invalid records are never persisted to PostgreSQL.

### Requirement 9: Error Handling and Graceful Degradation

**User Story:** As a platform operator, I want robust error handling throughout the system, so that failures in one component do not prevent partial results from being delivered.

#### Acceptance Criteria

1. IF PostgreSQL is unavailable during persistence, THEN THE Platform SHALL retry with exponential backoff starting at 1 second with a multiplier of 2 (delays of 1s, 2s, 4s for 3 attempts) before marking the analysis as not persisted and returning results with a persisted field set to false and a warning message in the response indicating the persistence failure.
2. IF the uploaded file contains no parseable log entries, THEN THE Platform SHALL return an error message indicating no log entries were found without persisting any data.
3. IF the LLM returns an unparseable response, THEN THE Platform SHALL store the raw response text (truncated to 32000 characters maximum) in the rawResponse field of the analysis and continue returning the incident with exceptionCounts, errorDistribution, and canonical events but with null llmAnalysis structured fields.
4. IF the uploaded file exceeds 50 MB in size, THEN THE Platform SHALL reject the upload and return an error message indicating the file exceeds the maximum allowed size.
5. IF the uploaded file is not a readable text file, THEN THE Platform SHALL return an error message indicating the file format is not supported.
6. WHEN any component throws an unhandled exception during analysis, THE Platform SHALL log the error with a correlation identifier and return an error message indicating an unexpected failure occurred, including the correlation identifier for troubleshooting.
