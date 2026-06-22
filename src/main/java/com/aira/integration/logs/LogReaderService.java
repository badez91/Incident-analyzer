package com.aira.integration.logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads and filters application logs from local folders.
 * Currently: reads from configured local log directory.
 * Future: SSH to remote servers and grep logs.
 *
 * Filters logs by:
 * - Service name (matches folder or filename pattern)
 * - Keywords (exception type, error message)
 * - Time window (last N minutes)
 * - Log level (ERROR, WARN)
 */
@Service
public class LogReaderService {

    private static final Logger log = LoggerFactory.getLogger(LogReaderService.class);

    private final String logBasePath;
    private final boolean enabled;

    public LogReaderService(@Value("${logs.base-path:}") String logBasePath) {
        if (logBasePath == null || logBasePath.isBlank()) {
            this.logBasePath = null;
            this.enabled = false;
            log.info("Log reader not configured (logs.base-path is empty). Log integration disabled.");
        } else {
            this.logBasePath = logBasePath;
            this.enabled = Files.isDirectory(Path.of(logBasePath));
            if (enabled) {
                log.info("Log reader initialized: basePath={}", logBasePath);
            } else {
                log.warn("Log base path does not exist: {}", logBasePath);
            }
        }
    }

    /**
     * Search logs for relevant entries matching the given criteria.
     *
     * @param serviceName   service name to filter by (matches folder/file names)
     * @param keywords      keywords to grep for (exception types, error messages)
     * @param minutesBack   how far back to search (filters by file modification time)
     * @param maxLines      max log lines to return
     * @return matching log entries as a consolidated string
     */
    public LogSearchResult searchLogs(String serviceName, List<String> keywords,
                                       int minutesBack, int maxLines) {
        if (!enabled) {
            return LogSearchResult.empty("Log reader not configured");
        }

        log.info("Searching logs: service={}, keywords={}, minutesBack={}, maxLines={}",
                serviceName, keywords, minutesBack, maxLines);

        try {
            // Find relevant log files
            List<Path> logFiles = findLogFiles(serviceName, minutesBack);

            if (logFiles.isEmpty()) {
                return LogSearchResult.empty("No log files found for service: " + serviceName);
            }

            // Grep for keywords in the log files
            List<String> matchingLines = grepLogFiles(logFiles, keywords, maxLines);

            log.info("Found {} matching log lines across {} files", matchingLines.size(), logFiles.size());

            return new LogSearchResult(
                    matchingLines.size(),
                    logFiles.size(),
                    String.join("\n", matchingLines),
                    serviceName,
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Log search failed: {}", e.getMessage());
            return LogSearchResult.empty("Log search error: " + e.getMessage());
        }
    }

    /**
     * Find log files matching the service name within the time window.
     * Looks for:
     * - Files in subdirectory named after the service
     * - Files with service name in the filename
     * - Any .log files if no service match
     */
    private List<Path> findLogFiles(String serviceName, int minutesBack) throws IOException {
        Path basePath = Path.of(logBasePath);
        Instant cutoff = Instant.now().minus(minutesBack, ChronoUnit.MINUTES);

        List<Path> result = new ArrayList<>();

        // Strategy 1: Look for service-specific subdirectory
        Path serviceDir = basePath.resolve(serviceName);
        if (Files.isDirectory(serviceDir)) {
            try (Stream<Path> files = Files.walk(serviceDir, 2)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> isLogFile(p))
                        .filter(p -> isRecentFile(p, cutoff))
                        .forEach(result::add);
            }
        }

        // Strategy 2: Look for files with service name in filename
        if (result.isEmpty()) {
            try (Stream<Path> files = Files.walk(basePath, 3)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> isLogFile(p))
                        .filter(p -> p.getFileName().toString().toLowerCase().contains(serviceName.toLowerCase()))
                        .filter(p -> isRecentFile(p, cutoff))
                        .forEach(result::add);
            }
        }

        // Strategy 3: All recent log files (if nothing matched)
        if (result.isEmpty()) {
            try (Stream<Path> files = Files.walk(basePath, 3)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> isLogFile(p))
                        .filter(p -> isRecentFile(p, cutoff))
                        .limit(5)  // limit to avoid scanning too many files
                        .forEach(result::add);
            }
        }

        // Sort by modification time (newest first)
        result.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });

        return result;
    }

    /**
     * Grep log files for lines matching any of the keywords.
     * Also captures ERROR and WARN lines.
     */
    private List<String> grepLogFiles(List<Path> logFiles, List<String> keywords, int maxLines) {
        List<String> allMatches = new ArrayList<>();

        // Build search patterns (case-insensitive)
        List<String> patterns = new ArrayList<>();
        if (keywords != null) {
            patterns.addAll(keywords.stream().map(String::toLowerCase).toList());
        }
        // Always include ERROR level
        patterns.add("error");
        patterns.add("exception");

        for (Path file : logFiles) {
            if (allMatches.size() >= maxLines) break;

            try {
                List<String> lines = Files.readAllLines(file);
                // Read from end (most recent entries)
                for (int i = lines.size() - 1; i >= 0 && allMatches.size() < maxLines; i--) {
                    String line = lines.get(i);
                    String lower = line.toLowerCase();

                    if (patterns.stream().anyMatch(lower::contains)) {
                        allMatches.add(line);
                    }
                }
            } catch (IOException e) {
                log.debug("Could not read log file {}: {}", file, e.getMessage());
            }
        }

        // Reverse so oldest appears first (chronological order)
        Collections.reverse(allMatches);
        return allMatches;
    }

    private boolean isLogFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".log") || name.endsWith(".txt") || name.endsWith(".out")
                || name.contains("log");
    }

    private boolean isRecentFile(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isAfter(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Result of a log search operation.
     */
    public record LogSearchResult(
            int matchedLines,
            int filesSearched,
            String content,
            String serviceName,
            Instant searchedAt
    ) {
        public static LogSearchResult empty(String reason) {
            return new LogSearchResult(0, 0, reason, null, Instant.now());
        }

        public boolean hasContent() {
            return matchedLines > 0;
        }

        /**
         * Returns a compact version for LLM context (max chars).
         */
        public String toContextSnippet(int maxChars) {
            if (!hasContent()) return "";
            String snippet = content.length() > maxChars ? content.substring(0, maxChars) : content;
            return "[Server Logs] " + snippet;
        }
    }
}
