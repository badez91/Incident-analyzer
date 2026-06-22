package com.aira.integration.sourcecode;

import com.aira.document.service.ContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Reads source code from a local repository path.
 * Extracts relevant code context around referenced classes/methods.
 *
 * Strategy:
 * 1. Parse stack traces and class references from the ticket content
 * 2. Search the configured repo path for matching .java files
 * 3. Extract ±10 lines around the referenced line number
 * 4. Return as compact context for the LLM prompt
 *
 * Future: SSH to remote server to read source code.
 */
@Service
public class SourceCodeService {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeService.class);
    private static final int CONTEXT_LINES = 10; // lines above and below the reference

    private final List<Path> repoPaths;
    private final boolean enabled;
    private final ContentExtractor contentExtractor;

    public SourceCodeService(
            @Value("${sourcecode.repo-paths:}") String repoPathsConfig,
            ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;

        if (repoPathsConfig == null || repoPathsConfig.isBlank()) {
            this.repoPaths = List.of();
            this.enabled = false;
            log.info("Source code service not configured (sourcecode.repo-paths is empty).");
            return;
        }

        // Parse comma-separated repo paths
        this.repoPaths = Arrays.stream(repoPathsConfig.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .map(Path::of)
                .filter(p -> {
                    if (!Files.isDirectory(p)) {
                        log.warn("Repo path does not exist: {}", p);
                        return false;
                    }
                    return true;
                })
                .toList();

        this.enabled = !this.repoPaths.isEmpty();
        if (enabled) {
            log.info("Source code service initialized with {} repo paths: {}", repoPaths.size(), repoPaths);
        }
    }

    /**
     * Extract source code context from ticket content.
     * Parses class/method references, finds the files, extracts surrounding code.
     *
     * @param ticketText combined text from ticket (description + comments)
     * @return code context snippet ready for LLM prompt, or null if nothing found
     */
    public String extractCodeContext(String ticketText) {
        if (!enabled || ticketText == null) return null;

        // Step 1: Extract source references from the ticket
        List<ContentExtractor.SourceReference> refs = contentExtractor.extractSourceReferences(ticketText);
        if (refs.isEmpty()) {
            log.debug("No source code references found in ticket text");
            return null;
        }

        log.info("Found {} source code references, searching repos...", refs.size());

        // Step 2: For each reference, find the file and extract context
        StringBuilder codeContext = new StringBuilder();
        int found = 0;

        for (ContentExtractor.SourceReference ref : refs) {
            if (found >= 3) break; // max 3 code snippets

            String snippet = findAndExtract(ref);
            if (snippet != null) {
                codeContext.append(snippet).append("\n");
                found++;
            }
        }

        if (codeContext.isEmpty()) {
            log.debug("Source files not found in configured repos");
            return null;
        }

        String result = codeContext.toString().trim();
        // Cap at 600 chars for prompt budget
        if (result.length() > 600) {
            result = result.substring(0, 600) + "\n... [truncated]";
        }

        log.info("Extracted {} code snippets ({} chars)", found, result.length());
        return result;
    }

    /**
     * Find a source file and extract context around the referenced line.
     */
    private String findAndExtract(ContentExtractor.SourceReference ref) {
        // Convert class name to file path pattern
        String fileName = ref.fileName();
        if (fileName == null) return null;

        // Search all configured repo paths
        for (Path repoPath : repoPaths) {
            Path found = findFile(repoPath, fileName);
            if (found != null) {
                return extractSnippet(found, ref);
            }
        }
        return null;
    }

    /**
     * Search recursively for a Java file in the repo.
     */
    private Path findFile(Path repoPath, String fileName) {
        try (Stream<Path> walk = Files.walk(repoPath, 10)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.debug("Error searching repo {}: {}", repoPath, e.getMessage());
            return null;
        }
    }

    /**
     * Extract code snippet around the referenced line.
     * If no line number, extract the method signature area.
     */
    private String extractSnippet(Path file, ContentExtractor.SourceReference ref) {
        try {
            List<String> lines = Files.readAllLines(file);
            int targetLine = ref.lineNumber();

            StringBuilder sb = new StringBuilder();
            sb.append("[").append(file.getFileName()).append(":");

            if (targetLine > 0 && targetLine <= lines.size()) {
                // Extract ±CONTEXT_LINES around the target line
                sb.append(targetLine).append("]\n");
                int start = Math.max(0, targetLine - CONTEXT_LINES - 1);
                int end = Math.min(lines.size(), targetLine + CONTEXT_LINES);

                for (int i = start; i < end; i++) {
                    String prefix = (i == targetLine - 1) ? ">>> " : "    ";
                    sb.append(prefix).append(lines.get(i)).append("\n");
                }
            } else if (ref.methodName() != null) {
                // No line number — search for the method name
                sb.append(ref.methodName()).append("()]\n");
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(ref.methodName())) {
                        int start = Math.max(0, i - 3);
                        int end = Math.min(lines.size(), i + CONTEXT_LINES);
                        for (int j = start; j < end; j++) {
                            String prefix = (j == i) ? ">>> " : "    ";
                            sb.append(prefix).append(lines.get(j)).append("\n");
                        }
                        break;
                    }
                }
            }

            String snippet = sb.toString();
            return snippet.length() > 5 ? snippet : null; // skip if we got nothing useful
        } catch (IOException e) {
            log.debug("Could not read file {}: {}", file, e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Path> getRepoPaths() {
        return repoPaths;
    }
}
