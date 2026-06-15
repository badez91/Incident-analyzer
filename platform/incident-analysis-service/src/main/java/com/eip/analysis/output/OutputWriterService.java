package com.eip.analysis.output;

import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.IncidentAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OutputWriterService {

    private static final Logger log = LoggerFactory.getLogger(OutputWriterService.class);
    
    private final Path outputDir;
    private final ObjectMapper objectMapper;
    private final JiraTemplateRenderer jiraRenderer;
    private final ConfluenceTemplateRenderer confluenceRenderer;

    public OutputWriterService(
            @Value("${integration.output-dir:./output}") String outputDir,
            JiraTemplateRenderer jiraRenderer,
            ConfluenceTemplateRenderer confluenceRenderer) {
        this.outputDir = Path.of(outputDir);
        this.jiraRenderer = jiraRenderer;
        this.confluenceRenderer = confluenceRenderer;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
        initDirectories();
    }

    private void initDirectories() {
        try {
            Files.createDirectories(outputDir.resolve("jira/incidents"));
            Files.createDirectories(outputDir.resolve("jira/comments"));
            Files.createDirectories(outputDir.resolve("jira/updates"));
            Files.createDirectories(outputDir.resolve("confluence/rca"));
            Files.createDirectories(outputDir.resolve("confluence/postmortem"));
            Files.createDirectories(outputDir.resolve("confluence/investigation"));
            log.info("Output directory initialized: {}", outputDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create output directories: {}", e.getMessage());
        }
    }

    public void generateOutputs(CanonicalIncidentEvent incident, IncidentAnalysisResult analysis) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String serviceSlug = slugify(incident.serviceName());
        String summarySlug = slugify(analysis.summary() != null ? analysis.summary() : "analysis");

        // Generate Confluence RCA document
        try {
            String rcaContent = confluenceRenderer.renderRCA(incident, analysis);
            String rcaFilename = String.format("%s_%s_%s.md", date, serviceSlug, summarySlug);
            Path rcaPath = outputDir.resolve("confluence/rca/" + rcaFilename);
            Files.writeString(rcaPath, rcaContent);
            log.info("Generated Confluence RCA: {}", rcaPath);
        } catch (IOException e) {
            log.error("Failed to write Confluence RCA: {}", e.getMessage());
        }

        // Generate Jira comment
        try {
            String commentContent = jiraRenderer.renderRCAComment(incident, analysis);
            String commentFilename = String.format("%s_%s_rca-comment.md", date, serviceSlug);
            Path commentPath = outputDir.resolve("jira/comments/" + commentFilename);
            Files.writeString(commentPath, commentContent);
            log.info("Generated Jira RCA comment: {}", commentPath);
        } catch (IOException e) {
            log.error("Failed to write Jira comment: {}", e.getMessage());
        }

        // Generate Jira ticket JSON
        try {
            String ticketJson = jiraRenderer.renderTicketJson(incident, analysis);
            String ticketFilename = String.format("%s_%s_incident.json", date, serviceSlug);
            Path ticketPath = outputDir.resolve("jira/incidents/" + ticketFilename);
            Files.writeString(ticketPath, ticketJson);
            log.info("Generated Jira ticket: {}", ticketPath);
        } catch (IOException e) {
            log.error("Failed to write Jira ticket: {}", e.getMessage());
        }

        // Update index
        updateIndex(incident, analysis, date, serviceSlug);
    }

    private void updateIndex(CanonicalIncidentEvent incident, IncidentAnalysisResult analysis, String date, String serviceSlug) {
        try {
            Path indexPath = outputDir.resolve("index.json");
            List<Map<String, Object>> outputs = new ArrayList<>();
            if (Files.exists(indexPath)) {
                try { outputs = objectMapper.readValue(indexPath.toFile(), List.class); } catch (Exception e) { /* start fresh */ }
            }
            outputs.add(Map.of(
                "incidentId", incident.incidentId().toString(),
                "service", incident.serviceName(),
                "severity", analysis.severity() != null ? analysis.severity() : "UNKNOWN",
                "generatedAt", Instant.now().toString(),
                "files", List.of(
                    "confluence/rca/" + date + "_" + serviceSlug + "_*.md",
                    "jira/comments/" + date + "_" + serviceSlug + "_rca-comment.md",
                    "jira/incidents/" + date + "_" + serviceSlug + "_incident.json"
                )
            ));
            objectMapper.writeValue(indexPath.toFile(), outputs);
        } catch (IOException e) {
            log.error("Failed to update index.json: {}", e.getMessage());
        }
    }

    private String slugify(String text) {
        if (text == null) return "unknown";
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "").substring(0, Math.min(text.length(), 50));
    }
}
