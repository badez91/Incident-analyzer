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

    // Stop words to exclude from keyword extraction
    private static final List<String> STOP_WORDS = List.of(
            "the", "is", "at", "which", "on", "a", "an", "and", "or", "but",
            "in", "with", "to", "for", "of", "not", "no", "can", "had", "has",
            "have", "was", "were", "been", "be", "are", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "shall",
            "this", "that", "these", "those", "it", "its", "from", "by",
            "please", "check", "assist", "advice", "already", "also", "what",
            "customer", "details", "company", "system", "application"
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

    /**
     * Extract meaningful keywords from text for Jira JQL text search.
     * Filters out stop words, short words, and Jira markup.
     * Returns the most meaningful terms for similarity matching.
     */
    public List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Clean Jira markup and formatting
        String cleaned = text
                .replaceAll("\\[~accountid:[^]]+\\]", "")  // Remove user mentions
                .replaceAll("\\[\\^[^]]+\\]", "")          // Remove attachment refs
                .replaceAll("![^!]+!", "")                   // Remove image markup
                .replaceAll("\\{[^}]+\\}", "")              // Remove {noformat} etc.
                .replaceAll("\\[INC\\d+\\]", "")            // Remove INC refs
                .replaceAll("[\\[\\]{}*_~^|#\\\\]", " ")    // Remove markup chars
                .replaceAll("https?://\\S+", "")            // Remove URLs
                .replaceAll("\\d{4}[/-]\\d{2}[/-]\\d{2}", "") // Remove dates
                .replaceAll("[^a-zA-Z\\s]", " ")            // Keep only letters
                .replaceAll("\\s+", " ")                    // Normalize whitespace
                .trim()
                .toLowerCase();

        // Split into words and filter
        String[] words = cleaned.split("\\s+");
        List<String> keywords = new ArrayList<>();

        for (String word : words) {
            if (word.length() < 3) continue;                    // Skip short words
            if (STOP_WORDS.contains(word)) continue;            // Skip stop words
            if (keywords.contains(word)) continue;              // Skip duplicates
            keywords.add(word);
        }

        // Return top keywords (most unique/meaningful)
        return keywords.stream()
                .limit(8)
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

    // --- RCA Comment Detection ---

    private static final List<String> RCA_INDICATORS = List.of(
            "root cause", "rca", "timeline", "resolution",
            "preventive action", "corrective action", "what happened",
            "root-cause", "post-mortem", "postmortem", "investigation",
            "caused by", "due to", "resolved by", "fix applied"
    );

    /**
     * Detect if a comment contains RCA/investigation content.
     * Returns the full comment text if it looks like an RCA, null otherwise.
     * This is a lightweight check — not the main analysis path.
     */
    public String extractRcaComment(List<String> comments) {
        if (comments == null || comments.isEmpty()) return null;

        for (String comment : comments) {
            if (comment == null || comment.length() < 100) continue; // RCA comments are substantial

            String lower = comment.toLowerCase();
            int matchCount = 0;
            for (String indicator : RCA_INDICATORS) {
                if (lower.contains(indicator)) matchCount++;
            }

            // If 2+ indicators match, this is likely an RCA comment
            if (matchCount >= 2) {
                // Return up to 1500 chars of the RCA comment
                return comment.length() > 1500 ? comment.substring(0, 1500) : comment;
            }
        }
        return null;
    }

    // --- Stack Trace / Source Code Reference Extraction ---

    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "at\\s+([a-zA-Z0-9_.]+)\\.([a-zA-Z0-9_]+)\\(([A-Za-z0-9_]+\\.java):(\\d+)\\)"
    );

    private static final Pattern CLASS_REFERENCE_PATTERN = Pattern.compile(
            "\\b([A-Z][a-zA-Z0-9]*(?:Service|Controller|Manager|Processor|Handler|Repository|Batch|Job|Task|Config|Util|Helper))\\.([a-zA-Z0-9_]+)\\(\\)"
    );

    /**
     * Extract source code references from text (stack traces, class.method mentions).
     * Returns list of SourceReference (className, methodName, fileName, lineNumber).
     */
    public List<SourceReference> extractSourceReferences(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<SourceReference> refs = new ArrayList<>();

        // From stack traces: "at com.example.MyClass.myMethod(MyClass.java:42)"
        Matcher stackMatcher = STACK_TRACE_PATTERN.matcher(text);
        while (stackMatcher.find()) {
            String fullClass = stackMatcher.group(1);
            String method = stackMatcher.group(2);
            String file = stackMatcher.group(3);
            int line = Integer.parseInt(stackMatcher.group(4));

            // Skip framework classes
            if (fullClass.startsWith("java.") || fullClass.startsWith("org.springframework.")
                    || fullClass.startsWith("sun.") || fullClass.startsWith("jdk.")) continue;

            refs.add(new SourceReference(fullClass, method, file, line));
        }

        // From class.method() mentions: "CusConManager.addContactPerson()"
        Matcher classMatcher = CLASS_REFERENCE_PATTERN.matcher(text);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String method = classMatcher.group(2);
            refs.add(new SourceReference(className, method, className + ".java", -1));
        }

        // Deduplicate by file
        return refs.stream()
                .distinct()
                .limit(5)
                .toList();
    }

    /**
     * Represents a reference to a source code location.
     */
    public record SourceReference(
            String className,
            String methodName,
            String fileName,
            int lineNumber
    ) {}
}
