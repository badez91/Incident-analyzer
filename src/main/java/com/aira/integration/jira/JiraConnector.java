package com.aira.integration.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JiraConnector {

    private static final Logger log = LoggerFactory.getLogger(JiraConnector.class);

    private final WebClient webClient;
    private final boolean enabled;
    private final String encodedCredentials;

    public JiraConnector(
            @Value("${jira.base-url:}") String baseUrl,
            @Value("${jira.username:}") String username,
            @Value("${jira.api-token:}") String apiToken) {

        if (apiToken == null || apiToken.isBlank()) {
            log.warn("Jira API token is not configured. Jira integration is disabled.");
            this.enabled = false;
            this.webClient = null;
            this.encodedCredentials = null;
            return;
        }

        this.enabled = true;
        String credentials = username + ":" + apiToken;
        this.encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encodedCredentials)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))  // 10MB buffer
                .build();

        log.info("Jira connector initialized: baseUrl={}", baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Optional<JiraIssueDto> getIssue(String key) {
        if (!enabled) {
            log.warn("Jira is disabled — cannot fetch issue {}", key);
            return Optional.empty();
        }

        // Jira keys are case-sensitive — always uppercase
        String normalizedKey = key.toUpperCase();

        try {
            // Try v3 first (Jira Cloud migrating to v3), then v2 as fallback
            Map<String, Object> response = null;
            try {
                response = webClient.get()
                        .uri("/rest/api/3/issue/{key}", normalizedKey)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
            } catch (Exception e3) {
                log.debug("API v3 returned {}, falling back to v2", e3.getMessage());
                try {
                    response = webClient.get()
                            .uri("/rest/api/2/issue/{key}", normalizedKey)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();
                } catch (Exception e2) {
                    log.error("Both API v3 and v2 failed for {}: v3={}, v2={}",
                            normalizedKey, e3.getMessage(), e2.getMessage());
                    return Optional.empty();
                }
            }

            if (response == null) return Optional.empty();
            return Optional.of(mapToDto(response));
        } catch (Exception e) {
            log.error("Failed to fetch Jira issue {}: {}", normalizedKey, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public List<JiraIssueDto> searchIssues(String jql, int maxResults) {
        if (!enabled) {
            log.warn("Jira is disabled — cannot search issues");
            return List.of();
        }

        try {
            // Jira Cloud new endpoint: POST /rest/api/3/search/jql
            Map<String, Object> body = Map.of(
                    "jql", jql,
                    "maxResults", maxResults,
                    "fields", List.of("summary", "description", "issuetype", "priority", "status",
                            "assignee", "labels", "comment", "attachment", "created", "updated")
            );

            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/3/search/jql")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of();

            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
            if (issues == null) return List.of();

            return issues.stream()
                    .map(this::mapToDto)
                    .toList();
        } catch (Exception e) {
            log.error("Jira search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Search for similar RESOLVED tickets of the same type in the same project.
     *
     * Strategy:
     * 1. Same issue type (Bug, Incident, etc.)
     * 2. Same project (extracted from key prefix)
     * 3. Status in resolved/done/complete states (prioritized — these have RCA)
     * 4. Keyword matching in summary/description
     * 5. Excludes the current ticket
     */
    public List<JiraIssueDto> findSimilarResolved(String currentKey, String issueType,
                                                   List<String> keywords, int maxResults) {
        if (!enabled) return List.of();

        String project = extractProject(currentKey);
        String keywordJql = buildKeywordJql(keywords);

        // Build JQL: same project + same type + resolved/done + keyword match + exclude self
        String jql = String.format(
                "project = %s AND issuetype = \"%s\" AND status in (Done, Resolved, Closed, Complete, \"RESOLUTION PROVIDED\", \"DEVELOPMENT COMPLETE\") " +
                "AND key != %s%s ORDER BY updated DESC",
                project, issueType, currentKey,
                keywordJql.isEmpty() ? "" : " AND " + keywordJql
        );

        log.info("Searching for similar resolved tickets: {}", jql);

        List<JiraIssueDto> results = searchSimilar(jql, maxResults);

        // If keyword search returned nothing, try without keywords (broader search, fewer results)
        if (results.isEmpty() && !keywordJql.isEmpty()) {
            String broaderJql = String.format(
                    "project = %s AND issuetype = \"%s\" AND status in (Done, Resolved, Closed, Complete, \"RESOLUTION PROVIDED\", \"DEVELOPMENT COMPLETE\") " +
                    "AND key != %s ORDER BY updated DESC",
                    project, issueType, currentKey
            );
            log.info("No keyword matches, trying broader search: {}", broaderJql);
            results = searchSimilar(broaderJql, maxResults);
        }

        log.info("Found {} similar resolved tickets for {}", results.size(), currentKey);
        return results;
    }

    /**
     * Lightweight search for similarity — fetches only essential fields
     * to minimize payload and speed up response.
     */
    @SuppressWarnings("unchecked")
    private List<JiraIssueDto> searchSimilar(String jql, int maxResults) {
        try {
            Map<String, Object> body = Map.of(
                    "jql", jql,
                    "maxResults", maxResults,
                    "fields", List.of("summary", "description", "issuetype", "status", "comment")
            );

            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/3/search/jql")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of();

            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
            if (issues == null) return List.of();

            return issues.stream()
                    .map(this::mapToDto)
                    .toList();
        } catch (Exception e) {
            log.error("Jira similarity search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Downloads attachment content as byte array.
     */
    public Optional<byte[]> downloadAttachment(String contentUrl) {
        if (!enabled || contentUrl == null) return Optional.empty();

        try {
            byte[] content = WebClient.create()
                    .get()
                    .uri(contentUrl)
                    .header("Authorization", "Basic " + encodedCredentials)
                    .header("Accept", "*/*")
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            return Optional.ofNullable(content);
        } catch (Exception e) {
            log.warn("Failed to download attachment from {}: {}", contentUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extract project key from issue key (e.g., "CM-5553" → "CM")
     */
    private String extractProject(String key) {
        if (key == null) return "";
        int dash = key.indexOf('-');
        return dash > 0 ? key.substring(0, dash) : key;
    }

    /**
     * Build JQL text search clause from keywords.
     * Uses Jira text search: text ~ "keyword1 keyword2"
     */
    private String buildKeywordJql(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return "";

        // Take top 4 most meaningful keywords (avoid noise)
        List<String> filtered = keywords.stream()
                .filter(k -> k.length() > 2)  // skip very short words
                .limit(4)
                .toList();

        if (filtered.isEmpty()) return "";

        // Jira text search: text ~ "keyword1 keyword2"
        return "text ~ \"" + String.join(" ", filtered) + "\"";
    }

    @SuppressWarnings("unchecked")
    private JiraIssueDto mapToDto(Map<String, Object> issue) {
        String key = (String) issue.get("key");
        Map<String, Object> fields = (Map<String, Object>) issue.getOrDefault("fields", Map.of());

        String summary = (String) fields.get("summary");
        
        // Description: API v3 returns ADF (JSON object), API v2 returns plain text
        String description;
        Object descObj = fields.get("description");
        if (descObj instanceof String) {
            description = (String) descObj;
        } else if (descObj instanceof Map) {
            // ADF format — extract text content from the document structure
            description = extractTextFromAdf((Map<String, Object>) descObj);
        } else {
            description = null;
        }

        // Issue type
        Map<String, Object> issueTypeObj = (Map<String, Object>) fields.get("issuetype");
        String issueType = issueTypeObj != null ? (String) issueTypeObj.get("name") : null;

        Map<String, Object> priorityObj = (Map<String, Object>) fields.get("priority");
        String priority = priorityObj != null ? (String) priorityObj.get("name") : null;

        Map<String, Object> statusObj = (Map<String, Object>) fields.get("status");
        String status = statusObj != null ? (String) statusObj.get("name") : null;

        Map<String, Object> assigneeObj = (Map<String, Object>) fields.get("assignee");
        String assignee = assigneeObj != null ? (String) assigneeObj.get("displayName") : null;

        List<String> labels = (List<String>) fields.getOrDefault("labels", List.of());

        // Extract comments (v3: body may be ADF object, v2: plain text)
        List<String> comments = new ArrayList<>();
        Map<String, Object> commentObj = (Map<String, Object>) fields.get("comment");
        if (commentObj != null) {
            List<Map<String, Object>> commentList = (List<Map<String, Object>>) commentObj.get("comments");
            if (commentList != null) {
                for (Map<String, Object> c : commentList) {
                    Object bodyObj = c.get("body");
                    String body;
                    if (bodyObj instanceof String) {
                        body = (String) bodyObj;
                    } else if (bodyObj instanceof Map) {
                        body = extractTextFromAdf((Map<String, Object>) bodyObj);
                    } else {
                        body = null;
                    }
                    if (body != null && !body.isBlank()) comments.add(body);
                }
            }
        }

        // Extract attachments
        List<JiraAttachmentDto> attachments = new ArrayList<>();
        List<Map<String, Object>> attachmentList = (List<Map<String, Object>>) fields.get("attachment");
        if (attachmentList != null) {
            for (Map<String, Object> att : attachmentList) {
                String id = att.get("id") != null ? att.get("id").toString() : null;
                String filename = (String) att.get("filename");
                String mimeType = (String) att.get("mimeType");
                long size = att.get("size") != null ? ((Number) att.get("size")).longValue() : 0;
                String contentUrl = (String) att.get("content");
                String created2 = (String) att.get("created");
                attachments.add(new JiraAttachmentDto(id, filename, mimeType, size, contentUrl, created2));
            }
        }

        String created = (String) fields.get("created");
        String updated = (String) fields.get("updated");

        return new JiraIssueDto(key, summary, description, issueType, priority, status, assignee,
                labels, comments, attachments, created, updated);
    }

    /**
     * Extract plain text from Jira ADF (Atlassian Document Format) structure.
     * ADF is a nested JSON tree; we recursively extract all text nodes.
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromAdf(Map<String, Object> adfNode) {
        if (adfNode == null) return "";

        StringBuilder sb = new StringBuilder();

        // If this node has direct text
        if (adfNode.containsKey("text")) {
            sb.append(adfNode.get("text"));
        }

        // Recurse into content array
        Object content = adfNode.get("content");
        if (content instanceof List) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) content;
            for (Map<String, Object> child : children) {
                String childText = extractTextFromAdf(child);
                if (!childText.isEmpty()) {
                    // Add newline between block-level nodes
                    String type = (String) child.getOrDefault("type", "");
                    if (type.equals("paragraph") || type.equals("heading") ||
                        type.equals("bulletList") || type.equals("orderedList") ||
                        type.equals("table") || type.equals("codeBlock") ||
                        type.equals("blockquote") || type.equals("rule")) {
                        if (!sb.isEmpty()) sb.append("\n");
                    }
                    sb.append(childText);
                }
            }
        }

        return sb.toString();
    }
}
