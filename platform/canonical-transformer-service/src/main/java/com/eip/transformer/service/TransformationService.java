package com.eip.transformer.service;

import com.eip.common.dto.IncidentSubmission;
import com.eip.common.model.CanonicalEvent;
import com.eip.common.model.CanonicalIncidentEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TransformationService {

    private static final Pattern TIMESTAMP_ISO = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3})");
    private static final Pattern TIMESTAMP_SPACE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})");
    private static final Pattern TIMESTAMP_SLASH = Pattern.compile("(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(ERROR|WARN|WARNING|INFO|DEBUG|TRACE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_CLASS = Pattern.compile("\\b([A-Z][a-zA-Z]*(?:Exception|Error))\\b");
    private static final Pattern STACK_TRACE = Pattern.compile("^\\s+at\\s+");
    private static final Pattern FQ_CLASS = Pattern.compile("\\b([a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*\\.[A-Z][A-Za-z0-9]*)\\b");
    private static final Pattern BRACKETED_SERVICE = Pattern.compile("\\[([A-Za-z][A-Za-z0-9._-]+)]");
    private static final Set<String> CRITICAL_EXCEPTIONS = Set.of("OutOfMemoryError", "StackOverflowError", "ThreadDeath");

    public CanonicalIncidentEvent transform(IncidentSubmission submission) {
        UUID incidentId = UUID.randomUUID();

        // Combine all log content
        StringBuilder allLogs = new StringBuilder(submission.description());
        if (submission.logSnippets() != null) {
            for (String snippet : submission.logSnippets()) {
                allLogs.append("\n").append(snippet);
            }
        }
        String rawContent = allLogs.toString();

        // Parse lines
        List<CanonicalEvent> events = new ArrayList<>();
        Map<String, Integer> exceptionCounts = new LinkedHashMap<>();
        String[] lines = rawContent.split("\\r?\\n");

        for (String line : lines) {
            if (line.isBlank()) continue;

            Instant timestamp = extractTimestamp(line);
            String level = detectLevel(line);
            String service = detectService(line);
            String eventType = classifyEventType(line, level);
            String message = truncateMessage(line.trim());

            CanonicalEvent event = new CanonicalEvent(UUID.randomUUID(), incidentId, timestamp, level, service, eventType, message);
            events.add(event);

            if ("EXCEPTION".equals(eventType)) {
                Matcher m = EXCEPTION_CLASS.matcher(line);
                if (m.find()) {
                    exceptionCounts.merge(m.group(1), 1, Integer::sum);
                }
            }
        }

        // Calculate error distribution
        int totalExceptions = exceptionCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> errorDistribution = new LinkedHashMap<>();
        if (totalExceptions > 0) {
            for (Map.Entry<String, Integer> entry : exceptionCounts.entrySet()) {
                errorDistribution.put(entry.getKey(), Math.round((entry.getValue() * 1000.0) / totalExceptions) / 10.0);
            }
            // Adjust to sum to 100
            double sum = errorDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
            if (!errorDistribution.isEmpty() && Math.abs(sum - 100.0) > 0.01) {
                String lastKey = errorDistribution.keySet().stream().reduce((a, b) -> b).orElse(null);
                if (lastKey != null) errorDistribution.put(lastKey, errorDistribution.get(lastKey) + (100.0 - sum));
            }
        }

        // Derive severity
        String severity = deriveSeverity(exceptionCounts, totalExceptions);

        // Extract symptoms (top exception names)
        List<String> symptoms = new ArrayList<>(exceptionCounts.keySet());

        String source = submission.source() != null ? submission.source() : "MANUAL";
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("description", submission.description());
        if (submission.metadata() != null) rawPayload.put("metadata", submission.metadata());

        return new CanonicalIncidentEvent(
            incidentId, submission.serviceName(), severity, symptoms,
            Instant.now(), source, rawPayload, events, exceptionCounts,
            new LinkedHashSet<>(exceptionCounts.keySet()), errorDistribution,
            events.size(), totalExceptions
        );
    }

    private Instant extractTimestamp(String line) {
        Matcher m = TIMESTAMP_ISO.matcher(line);
        if (m.find()) {
            try { return LocalDateTime.parse(m.group(1), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")).toInstant(ZoneOffset.UTC); } catch (Exception e) { /* fall through */ }
        }
        m = TIMESTAMP_SPACE.matcher(line);
        if (m.find()) {
            try { return LocalDateTime.parse(m.group(1), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")).toInstant(ZoneOffset.UTC); } catch (Exception e) { /* fall through */ }
        }
        m = TIMESTAMP_SLASH.matcher(line);
        if (m.find()) {
            try { return LocalDateTime.parse(m.group(1), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")).toInstant(ZoneOffset.UTC); } catch (Exception e) { /* fall through */ }
        }
        return Instant.now();
    }

    private String detectLevel(String line) {
        Matcher m = LEVEL_PATTERN.matcher(line);
        if (m.find()) {
            String level = m.group(1).toUpperCase();
            return "WARNING".equals(level) ? "WARN" : level;
        }
        return "INFO";
    }

    private String detectService(String line) {
        Matcher m = BRACKETED_SERVICE.matcher(line);
        if (m.find()) {
            String candidate = m.group(1);
            if (!candidate.matches("\\d+.*") && !Set.of("error", "warn", "info", "debug", "main").contains(candidate.toLowerCase())) {
                return candidate;
            }
        }
        m = FQ_CLASS.matcher(line);
        if (m.find()) return m.group(1);
        return "UNKNOWN";
    }

    private String classifyEventType(String line, String level) {
        if (STACK_TRACE.matcher(line).find()) return "STACK_TRACE";
        if (EXCEPTION_CLASS.matcher(line).find()) return "EXCEPTION";
        if ("ERROR".equals(level)) return "ERROR";
        if ("WARN".equals(level)) return "WARNING";
        return "INFO";
    }

    private String truncateMessage(String msg) {
        if (msg == null) return "";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 4000) return msg;
        return new String(bytes, 0, 4000, StandardCharsets.UTF_8).replaceAll("\uFFFD$", "");
    }

    private String deriveSeverity(Map<String, Integer> exceptionCounts, int totalExceptions) {
        boolean hasCritical = exceptionCounts.keySet().stream().anyMatch(CRITICAL_EXCEPTIONS::contains);
        if (hasCritical || totalExceptions > 100) return "CRITICAL";
        if (totalExceptions > 50 || exceptionCounts.size() > 5) return "HIGH";
        if (totalExceptions > 10) return "MEDIUM";
        return "LOW";
    }
}
