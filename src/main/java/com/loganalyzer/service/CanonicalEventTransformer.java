package com.loganalyzer.service;

import com.loganalyzer.model.CanonicalEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms raw log text into normalized canonical events with consistent schema.
 * Supports multiple timestamp formats, level detection, service extraction,
 * and event type classification.
 */
@Service
public class CanonicalEventTransformer {

    // Timestamp patterns (order matters — try most specific first)
    private static final List<TimestampFormat> TIMESTAMP_FORMATS = List.of(
            new TimestampFormat(
                    Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            ),
            new TimestampFormat(
                    Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            ),
            new TimestampFormat(
                    Pattern.compile("(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
            ),
            new TimestampFormat(
                    Pattern.compile("(\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [+\\-]\\d{4})"),
                    DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z")
            )
    );

    // Level detection
    private static final Pattern LEVEL_PATTERN = Pattern.compile(
            "\\b(ERROR|WARN|WARNING|INFO|DEBUG|TRACE)\\b", Pattern.CASE_INSENSITIVE
    );

    // Service detection: fully-qualified class name or bracketed identifier
    private static final Pattern FQ_CLASS_PATTERN = Pattern.compile(
            "\\b([a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*\\.[A-Z][A-Za-z0-9]*)\\b"
    );
    private static final Pattern BRACKETED_SERVICE_PATTERN = Pattern.compile(
            "\\[([A-Za-z][A-Za-z0-9._-]+)]"
    );

    // Exception detection
    private static final Pattern EXCEPTION_CLASS_PATTERN = Pattern.compile(
            "\\b([A-Z][a-zA-Z]*(?:Exception|Error))\\b"
    );

    // Stack trace detection: line starts with whitespace + "at "
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "^\\s+at\\s+"
    );

    /**
     * Transform raw log content into a list of canonical events.
     * Preserves input ordering. Skips blank lines.
     */
    public List<CanonicalEvent> transform(String rawLogContent) {
        if (rawLogContent == null || rawLogContent.isBlank()) {
            return List.of();
        }

        List<CanonicalEvent> events = new ArrayList<>();
        String[] lines = rawLogContent.split("\\r?\\n");

        for (String line : lines) {
            if (line.isBlank()) continue;

            CanonicalEvent event = parseLine(line);
            if (event != null) {
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Parse a single log line into a CanonicalEvent.
     */
    public CanonicalEvent parseLine(String logLine) {
        if (logLine == null || logLine.isBlank()) {
            return null;
        }

        Instant timestamp = extractTimestamp(logLine);
        String level = detectLevel(logLine);
        String service = detectService(logLine);
        String eventType = classifyEventType(logLine, level);
        String message = truncateMessage(logLine.trim());

        CanonicalEvent event = new CanonicalEvent();
        event.setId(UUID.randomUUID());
        event.setTimestamp(timestamp);
        event.setLevel(level);
        event.setService(service);
        event.setEventType(eventType);
        event.setMessage(message);

        return event;
    }

    /**
     * Extract and normalize timestamp from a log line.
     * Falls back to current time if no recognized format found.
     */
    private Instant extractTimestamp(String logLine) {
        for (TimestampFormat tf : TIMESTAMP_FORMATS) {
            Matcher matcher = tf.pattern.matcher(logLine);
            if (matcher.find()) {
                try {
                    String timestampStr = matcher.group(1);
                    if (tf.formatter.toString().contains("Z")) {
                        // Pattern with timezone offset
                        return DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH)
                                .parse(timestampStr, java.time.ZonedDateTime::from)
                                .toInstant();
                    } else {
                        LocalDateTime ldt = LocalDateTime.parse(timestampStr, tf.formatter);
                        return ldt.toInstant(ZoneOffset.UTC);
                    }
                } catch (DateTimeParseException e) {
                    // Try next format
                }
            }
        }
        // Fallback: current processing time
        return Instant.now();
    }

    /**
     * Detect log level from line content.
     * Maps WARNING -> WARN. Defaults to INFO.
     */
    public String detectLevel(String logLine) {
        Matcher matcher = LEVEL_PATTERN.matcher(logLine);
        if (matcher.find()) {
            String level = matcher.group(1).toUpperCase();
            if ("WARNING".equals(level)) {
                return "WARN";
            }
            return level;
        }
        return "INFO";
    }

    /**
     * Detect service name from log line.
     * Looks for fully-qualified class names or bracketed identifiers.
     * Returns "UNKNOWN" if not found.
     */
    public String detectService(String logLine) {
        // Try bracketed service first (e.g., [payment-service])
        Matcher bracketMatcher = BRACKETED_SERVICE_PATTERN.matcher(logLine);
        if (bracketMatcher.find()) {
            String candidate = bracketMatcher.group(1);
            // Skip common non-service brackets like thread names starting with numbers
            if (!candidate.matches("\\d+.*") && !candidate.equalsIgnoreCase("main")
                    && !candidate.equalsIgnoreCase("ERROR") && !candidate.equalsIgnoreCase("WARN")
                    && !candidate.equalsIgnoreCase("INFO") && !candidate.equalsIgnoreCase("DEBUG")) {
                return candidate;
            }
        }

        // Try fully-qualified class name
        Matcher fqMatcher = FQ_CLASS_PATTERN.matcher(logLine);
        if (fqMatcher.find()) {
            return fqMatcher.group(1);
        }

        return "UNKNOWN";
    }

    /**
     * Classify event type based on line content and level.
     */
    public String classifyEventType(String logLine, String level) {
        // Stack trace line
        if (STACK_TRACE_PATTERN.matcher(logLine).find()) {
            return "STACK_TRACE";
        }

        // Exception class present
        if (EXCEPTION_CLASS_PATTERN.matcher(logLine).find()) {
            return "EXCEPTION";
        }

        // ERROR level without exception
        if ("ERROR".equals(level)) {
            return "ERROR";
        }

        // WARN level
        if ("WARN".equals(level)) {
            return "WARNING";
        }

        // Default
        return "INFO";
    }

    /**
     * Truncate message to 4000 bytes at a valid UTF-8 character boundary.
     */
    private String truncateMessage(String message) {
        if (message == null) return "";
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 4000) {
            return message;
        }

        // Truncate at valid character boundary
        String truncated = new String(bytes, 0, 4000, StandardCharsets.UTF_8);
        // Remove last char if it's a replacement char (partial multi-byte)
        if (truncated.endsWith("\uFFFD")) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated;
    }

    /**
     * Holds a regex pattern and its corresponding DateTimeFormatter.
     */
    private record TimestampFormat(Pattern pattern, DateTimeFormatter formatter) {}
}
