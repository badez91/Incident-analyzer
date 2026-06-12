package com.loganalyzer.controller;

import com.loganalyzer.model.AiAnalysis;
import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.model.IncidentAnalysis;
import com.loganalyzer.service.IncidentAnalysisService;
import com.loganalyzer.service.LokiService;
import com.loganalyzer.service.LogParserService;
import com.loganalyzer.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Controller
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);
    private static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB

    private final LogParserService logParserService;
    private final OllamaService ollamaService;
    private final LokiService lokiService;
    private final IncidentAnalysisService incidentAnalysisService;
    private final List<String> availableModels;
    private final String defaultModel;
    private final int defaultLokiLimit;

    public LogController(LogParserService logParserService,
                         OllamaService ollamaService,
                         LokiService lokiService,
                         IncidentAnalysisService incidentAnalysisService,
                         @Value("${ollama.model}") String defaultModel,
                         @Value("${ollama.models}") List<String> availableModels,
                         @Value("${grafana.loki.default-limit:5000}") int defaultLokiLimit) {
        this.logParserService = logParserService;
        this.ollamaService = ollamaService;
        this.lokiService = lokiService;
        this.incidentAnalysisService = incidentAnalysisService;
        this.defaultModel = defaultModel;
        this.availableModels = availableModels;
        this.defaultLokiLimit = defaultLokiLimit;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("models", availableModels);
        model.addAttribute("defaultModel", defaultModel);
        model.addAttribute("lokiEnabled", lokiService.isEnabled());
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "model", required = false) String selectedModel,
                          Model model) {
        model.addAttribute("models", availableModels);
        model.addAttribute("defaultModel", defaultModel);
        model.addAttribute("lokiEnabled", lokiService.isEnabled());

        // Validate file
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".log")) {
            model.addAttribute("error", "Please upload a .log file.");
            return "index";
        }

        if (file.isEmpty()) {
            model.addAttribute("error", "The uploaded file is empty.");
            return "index";
        }

        // Check file size (50MB max)
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            model.addAttribute("error", "File exceeds maximum allowed size of 50MB.");
            return "index";
        }

        // Read file content
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            model.addAttribute("error", "Failed to read the uploaded file: " + e.getMessage());
            return "index";
        }

        return analyzeContent(content, filename, selectedModel, model);
    }

    @PostMapping("/analyze-loki")
    public String analyzeLoki(
            @RequestParam("lokiQuery") String lokiQuery,
            @RequestParam(value = "timeRange", defaultValue = "1h") String timeRange,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "model", required = false) String selectedModel,
            Model model) {

        model.addAttribute("models", availableModels);
        model.addAttribute("defaultModel", defaultModel);
        model.addAttribute("lokiEnabled", lokiService.isEnabled());

        if (!lokiService.isEnabled()) {
            model.addAttribute("error", "Loki integration is not configured. Set grafana.url and grafana.service-account-token in application.properties.");
            return "index";
        }

        if (lokiQuery == null || lokiQuery.isBlank()) {
            model.addAttribute("error", "Please provide a LogQL query.");
            return "index";
        }

        // Parse time range
        Instant end = Instant.now();
        Instant start = parseTimeRange(end, timeRange);
        int queryLimit = (limit != null && limit > 0) ? limit : defaultLokiLimit;

        // Query Loki
        LokiService.LokiQueryResult lokiResult = lokiService.queryLogs(lokiQuery, start, end, queryLimit);

        if (lokiResult.isError()) {
            model.addAttribute("error", "Loki query failed: " + lokiResult.getErrorMessage());
            return "index";
        }

        if (!lokiResult.hasData()) {
            model.addAttribute("error", "No log entries found matching the query in the specified time range.");
            return "index";
        }

        // Use the Loki log content for analysis
        String sourceName = "Loki: " + lokiQuery + " (" + timeRange + ", " + lokiResult.getLineCount() + " lines)";
        return analyzeContent(lokiResult.getLogContent(), sourceName, selectedModel, model);
    }

    /**
     * API endpoint to get available Loki labels (for UI autocomplete).
     */
    @GetMapping("/api/loki/labels")
    @ResponseBody
    public List<String> getLokiLabels(@RequestParam(defaultValue = "app") String label) {
        return lokiService.getLabelValues(label);
    }

    /**
     * Common analysis logic shared between file upload and Loki query.
     * Performs the existing analysis AND persists to the knowledge store.
     */
    private String analyzeContent(String content, String sourceName, String selectedModel, Model model) {
        // Phase 1: parse and count exceptions
        Map<String, Integer> exceptionCounts = logParserService.parseLog(content);

        if (exceptionCounts.isEmpty()) {
            model.addAttribute("error", "No exceptions or errors found in the logs.");
            return "index";
        }

        // Phase 2: build enriched summary and call Ollama
        String summary = logParserService.buildEnrichedSummary(content);

        // Use selected model or fall back to default
        String modelToUse = (selectedModel != null && !selectedModel.isBlank())
                ? selectedModel : defaultModel;
        AiAnalysis aiAnalysis = ollamaService.getStructuredAnalysis(summary, modelToUse);

        // Build result for display
        AnalysisResult result = new AnalysisResult(sourceName, exceptionCounts, summary, null);
        model.addAttribute("result", result);
        model.addAttribute("aiAnalysis", aiAnalysis);
        model.addAttribute("selectedModel", modelToUse);

        // Phase 3: Persist to knowledge store (non-blocking — failures don't affect the UI)
        try {
            IncidentAnalysis incident = incidentAnalysisService.analyzeAndPersist(content, sourceName, modelToUse);
            model.addAttribute("incidentId", incident.getIncidentId().toString());
            log.info("Incident persisted to knowledge store: {}", incident.getIncidentId());
        } catch (IncidentAnalysisService.AnalysisException e) {
            // Expected — no exceptions found (shouldn't happen since we checked above)
            log.warn("Knowledge store persistence skipped: {}", e.getMessage());
        } catch (Exception e) {
            // Knowledge store failure should NOT block the user from seeing results
            log.error("Knowledge store persistence failed (non-fatal): {}", e.getMessage());
        }

        return "results";
    }

    /**
     * Parse a human-readable time range string into an Instant.
     * Supports: 15m, 30m, 1h, 3h, 6h, 12h, 24h, 7d
     */
    private Instant parseTimeRange(Instant end, String timeRange) {
        try {
            if (timeRange.endsWith("m")) {
                int minutes = Integer.parseInt(timeRange.replace("m", ""));
                return end.minus(minutes, ChronoUnit.MINUTES);
            } else if (timeRange.endsWith("h")) {
                int hours = Integer.parseInt(timeRange.replace("h", ""));
                return end.minus(hours, ChronoUnit.HOURS);
            } else if (timeRange.endsWith("d")) {
                int days = Integer.parseInt(timeRange.replace("d", ""));
                return end.minus(days, ChronoUnit.DAYS);
            }
        } catch (NumberFormatException e) {
            // fallback
        }
        // Default: 1 hour
        return end.minus(1, ChronoUnit.HOURS);
    }
}
