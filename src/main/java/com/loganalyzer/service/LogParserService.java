package com.loganalyzer.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses log text to extract, deduplicate, and count Java exceptions.
 * Produces a structured JSON summary for the LLM.
 */
@Service
public class LogParserService {

    // Max exceptions to include in the AI summary
    private static final int TOP_N = 10;

    // Max unique messages to include per exception
    private static final int MAX_MESSAGES_PER_EXCEPTION = 3;

    // Matches fully-qualified Java exceptions: "java.sql.SQLTransientConnectionException: message"
    private static final Pattern EXCEPTION_WITH_MESSAGE = Pattern.compile(
            "(?:Exception|Error|Caused by)[:\\s]+([a-zA-Z_]\\w*(?:\\.[a-zA-Z_]\\w*)*(?:Exception|Error))(?:[:\\s]+(.+))?"
    );

    // Matches ERROR log level lines
    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{4}[-/]\\d{2}[-/]\\d{2}[\\sT][\\d:.]+\\s+)?\\[?\\s*ERROR\\s*]?"
    );

    // Matches standalone exception class names like "NullPointerException: message"
    private static final Pattern STANDALONE_EXCEPTION_PATTERN = Pattern.compile(
            "\\b([A-Z][a-zA-Z]*(?:Exception|Error))\\b(?:[:\\s]+(.+))?"
    );

    // Extracts timestamp from log lines
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{4}[-/]\\d{2}[-/]\\d{2}[\\sT][\\d:.]+)"
    );

    /**
     * Holds parsed data for a single exception type.
     */
    public static class ExceptionInfo {
        private final String name;
        private int count;
        private final Set<String> messages = new LinkedHashSet<>();
        private String firstSeen;
        private String lastSeen;

        public ExceptionInfo(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public int getCount() { return count; }
        public Set<String> getMessages() { return messages; }
        public String getFirstSeen() { return firstSeen; }
        public String getLastSeen() { return lastSeen; }
    }

    /**
     * Parse log text and return exception counts sorted by count descending.
     * Used for the UI table display.
     */
    public Map<String, Integer> parseLog(String text) {
        Map<String, ExceptionInfo> detailed = parseLogDetailed(text);
        return detailed.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().count,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * Build a structured JSON summary from the log for the LLM.
     * JSON is unambiguous, compact, and easier for the model to parse.
     */
    public String buildEnrichedSummary(String text) {
        Map<String, ExceptionInfo> detailed = parseLogDetailed(text);

        // Sort by count descending, take top N
        List<ExceptionInfo> sorted = detailed.values().stream()
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .limit(TOP_N)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            return "{\"exceptions\":[]}";
        }

        // Detect time window
        String firstTimestamp = null;
        String lastTimestamp = null;
        for (ExceptionInfo info : sorted) {
            if (info.firstSeen != null) {
                if (firstTimestamp == null || info.firstSeen.compareTo(firstTimestamp) < 0) {
                    firstTimestamp = info.firstSeen;
                }
            }
            if (info.lastSeen != null) {
                if (lastTimestamp == null || info.lastSeen.compareTo(lastTimestamp) > 0) {
                    lastTimestamp = info.lastSeen;
                }
            }
        }

        int totalExceptions = sorted.stream().mapToInt(e -> e.count).sum();

        // Build JSON manually (no extra dependency needed)
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timeWindow\": {");
        json.append("\"start\": ").append(quote(firstTimestamp)).append(", ");
        json.append("\"end\": ").append(quote(lastTimestamp));
        json.append("},\n");
        json.append("  \"totalExceptions\": ").append(totalExceptions).append(",\n");
        json.append("  \"uniqueTypes\": ").append(sorted.size()).append(",\n");
        json.append("  \"exceptions\": [\n");

        for (int i = 0; i < sorted.size(); i++) {
            ExceptionInfo info = sorted.get(i);
            json.append("    {\n");
            json.append("      \"rank\": ").append(i + 1).append(",\n");
            json.append("      \"exception\": ").append(quote(info.name)).append(",\n");
            json.append("      \"count\": ").append(info.count).append(",\n");
            json.append("      \"percentage\": ").append(String.format("%.1f", (info.count * 100.0) / totalExceptions)).append(",\n");

            // Messages
            List<String> msgs = info.messages.stream()
                    .filter(m -> m != null && !m.isBlank() && m.length() > 3)
                    .limit(MAX_MESSAGES_PER_EXCEPTION)
                    .collect(Collectors.toList());
            json.append("      \"sampleMessages\": [");
            json.append(msgs.stream()
                    .map(m -> quote(truncate(m, 100)))
                    .collect(Collectors.joining(", ")));
            json.append("],\n");

            // Time info
            json.append("      \"firstSeen\": ").append(quote(info.firstSeen)).append(",\n");
            json.append("      \"lastSeen\": ").append(quote(info.lastSeen)).append("\n");

            json.append("    }");
            if (i < sorted.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Simple summary (fallback, used for UI display).
     */
    public String buildSummary(Map<String, Integer> exceptionCounts) {
        StringBuilder sb = new StringBuilder("Log Analysis Summary\n");
        int count = 0;
        for (Map.Entry<String, Integer> entry : exceptionCounts.entrySet()) {
            if (count++ >= TOP_N) break;
            sb.append(entry.getValue())
              .append(" occurrences: ")
              .append(entry.getKey())
              .append("\n");
        }
        return sb.toString().trim();
    }

    // --- Detailed parsing ---

    private Map<String, ExceptionInfo> parseLogDetailed(String text) {
        Map<String, ExceptionInfo> infoMap = new HashMap<>();
        Map<String, String> shortToFull = new HashMap<>();
        String currentTimestamp = null;

        for (String line : text.split("\\r?\\n")) {
            Matcher tsMatcher = TIMESTAMP_PATTERN.matcher(line);
            if (tsMatcher.find()) {
                currentTimestamp = tsMatcher.group(1);
            }

            Matcher fqMatcher = EXCEPTION_WITH_MESSAGE.matcher(line);
            boolean foundFq = false;
            while (fqMatcher.find()) {
                String fullName = fqMatcher.group(1);
                String message = fqMatcher.group(2);
                String shortName = getShortName(fullName);
                shortToFull.put(shortName, fullName);
                addOccurrence(infoMap, fullName, message, currentTimestamp);
                foundFq = true;
            }
            if (foundFq) continue;

            boolean isErrorLine = ERROR_LINE_PATTERN.matcher(line).find();
            if (isErrorLine || line.contains("Exception") || line.contains("Error")) {
                Matcher standalone = STANDALONE_EXCEPTION_PATTERN.matcher(line);
                while (standalone.find()) {
                    String name = standalone.group(1);
                    String message = standalone.group(2);
                    addOccurrence(infoMap, name, message, currentTimestamp);
                }
            }
        }

        // Deduplicate short names into fully-qualified versions
        Map<String, ExceptionInfo> deduplicated = new LinkedHashMap<>();
        for (Map.Entry<String, ExceptionInfo> entry : infoMap.entrySet()) {
            String name = entry.getKey();
            ExceptionInfo info = entry.getValue();

            if (shortToFull.containsKey(name) && !name.contains(".")) {
                String fullName = shortToFull.get(name);
                ExceptionInfo fullInfo = deduplicated.computeIfAbsent(fullName,
                        k -> infoMap.getOrDefault(fullName, new ExceptionInfo(fullName)));
                mergeInfo(fullInfo, info);
            } else {
                deduplicated.merge(name, info, (existing, newInfo) -> {
                    mergeInfo(existing, newInfo);
                    return existing;
                });
            }
        }

        return deduplicated;
    }

    // --- Helpers ---

    private void addOccurrence(Map<String, ExceptionInfo> map, String name,
                               String message, String timestamp) {
        ExceptionInfo info = map.computeIfAbsent(name, ExceptionInfo::new);
        info.count++;
        if (message != null && !message.isBlank()) {
            String trimmed = message.trim();
            if (info.messages.size() < MAX_MESSAGES_PER_EXCEPTION * 2) {
                info.messages.add(trimmed);
            }
        }
        if (timestamp != null) {
            if (info.firstSeen == null || timestamp.compareTo(info.firstSeen) < 0) {
                info.firstSeen = timestamp;
            }
            if (info.lastSeen == null || timestamp.compareTo(info.lastSeen) > 0) {
                info.lastSeen = timestamp;
            }
        }
    }

    private void mergeInfo(ExceptionInfo target, ExceptionInfo source) {
        target.count += source.count;
        for (String msg : source.messages) {
            if (target.messages.size() < MAX_MESSAGES_PER_EXCEPTION * 2) {
                target.messages.add(msg);
            }
        }
        if (source.firstSeen != null) {
            if (target.firstSeen == null || source.firstSeen.compareTo(target.firstSeen) < 0) {
                target.firstSeen = source.firstSeen;
            }
        }
        if (source.lastSeen != null) {
            if (target.lastSeen == null || source.lastSeen.compareTo(target.lastSeen) > 0) {
                target.lastSeen = source.lastSeen;
            }
        }
    }

    private String getShortName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    private String truncate(String str, int maxLen) {
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    private String quote(String value) {
        if (value == null) return "null";
        // Escape special JSON characters
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
