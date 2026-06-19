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
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.encodedCredentials = encodedCredentials;

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encodedCredentials)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();

        log.info("Jira connector initialized: baseUrl={}", baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Optional<JiraIssueDto> getIssue(String key) {
        if (!enabled) {
            log.warn("Jira is disabled — cannot fetch issue {}", key);
            return Optional.empty();
        }

        try {
            Map<String, Object> response = webClient.get()
                    .uri("/rest/api/2/issue/{key}", key)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return Optional.empty();
            return Optional.of(mapToDto(response));
        } catch (Exception e) {
            log.error("Failed to fetch Jira issue {}: {}", key, e.getMessage());
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
            Map<String, Object> body = Map.of(
                    "jql", jql,
                    "maxResults", maxResults,
                    "fields", List.of("summary", "description", "priority", "status",
                            "assignee", "labels", "comment", "attachment", "created", "updated")
            );

            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/2/search")
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
            log.error("Failed to search Jira issues: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Downloads attachment content as byte array.
     * Uses a separate WebClient call with the full absolute URL from Jira.
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

    @SuppressWarnings("unchecked")
    private JiraIssueDto mapToDto(Map<String, Object> issue) {
        String key = (String) issue.get("key");
        Map<String, Object> fields = (Map<String, Object>) issue.getOrDefault("fields", Map.of());

        String summary = (String) fields.get("summary");
        String description = (String) fields.get("description");

        Map<String, Object> priorityObj = (Map<String, Object>) fields.get("priority");
        String priority = priorityObj != null ? (String) priorityObj.get("name") : null;

        Map<String, Object> statusObj = (Map<String, Object>) fields.get("status");
        String status = statusObj != null ? (String) statusObj.get("name") : null;

        Map<String, Object> assigneeObj = (Map<String, Object>) fields.get("assignee");
        String assignee = assigneeObj != null ? (String) assigneeObj.get("displayName") : null;

        List<String> labels = (List<String>) fields.getOrDefault("labels", List.of());

        // Extract comments
        List<String> comments = new ArrayList<>();
        Map<String, Object> commentObj = (Map<String, Object>) fields.get("comment");
        if (commentObj != null) {
            List<Map<String, Object>> commentList = (List<Map<String, Object>>) commentObj.get("comments");
            if (commentList != null) {
                for (Map<String, Object> c : commentList) {
                    String body = (String) c.get("body");
                    if (body != null) comments.add(body);
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

        return new JiraIssueDto(key, summary, description, priority, status, assignee,
                labels, comments, attachments, created, updated);
    }
}
