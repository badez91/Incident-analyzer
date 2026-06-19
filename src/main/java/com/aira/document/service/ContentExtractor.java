package com.aira.document.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ContentExtractor {

    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "\\b([A-Z][a-zA-Z0-9]*(?:Exception|Error))\\b"
    );

    private static final List<String> ERROR_BEHAVIORS = List.of(
            "failed", "timeout", "unavailable", "error", "hang", "crash",
            "connection refused", "out of memory", "deadlock", "retry",
            "rejected", "overflow", "null pointer", "stack overflow"
    );

    private static final List<String> COMPONENT_KEYWORDS = List.of(
            "batch", "cron", "upload", "report", "payment", "gateway",
            "scheduler", "queue", "cache", "database", "api", "auth",
            "notification", "email", "sms", "webhook", "integration"
    );

    public List<String> extractExceptionTypes(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher matcher = EXCEPTION_PATTERN.matcher(text);
        List<String> exceptions = new ArrayList<>();
        while (matcher.find()) {
            String match = matcher.group(1);
            if (!exceptions.contains(match)) {
                exceptions.add(match);
            }
        }
        return exceptions;
    }

    public List<String> extractErrorBehaviors(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase();
        return ERROR_BEHAVIORS.stream()
                .filter(lower::contains)
                .collect(Collectors.toList());
    }

    public List<String> extractComponents(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase();
        return COMPONENT_KEYWORDS.stream()
                .filter(lower::contains)
                .collect(Collectors.toList());
    }

    public String extractServiceName(String jiraKey) {
        if (jiraKey == null || jiraKey.isBlank()) return "unknown";
        int dashIndex = jiraKey.indexOf('-');
        if (dashIndex > 0) {
            return jiraKey.substring(0, dashIndex).toLowerCase();
        }
        return jiraKey.toLowerCase();
    }

    public String buildSearchableText(String summary, String description, List<String> comments) {
        StringBuilder sb = new StringBuilder();
        if (summary != null) {
            sb.append(summary).append(" ");
        }
        if (description != null) {
            sb.append(description, 0, Math.min(description.length(), 1500)).append(" ");
        }
        if (comments != null && !comments.isEmpty()) {
            String firstComment = comments.get(0);
            sb.append(firstComment, 0, Math.min(firstComment.length(), 500));
        }
        String result = sb.toString().trim();
        return result.length() > 2000 ? result.substring(0, 2000) : result;
    }

    public String buildSummary(String title, List<String> exceptions, List<String> behaviors) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (exceptions != null && !exceptions.isEmpty()) {
            sb.append(" | Exceptions: ").append(String.join(", ", exceptions));
        }
        if (behaviors != null && !behaviors.isEmpty()) {
            sb.append(" | Behaviors: ").append(String.join(", ", behaviors));
        }
        String result = sb.toString().trim();
        return result.length() > 500 ? result.substring(0, 500) : result;
    }
}
