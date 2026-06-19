package com.aira.document.service;

import com.aira.integration.jira.JiraAttachmentDto;
import com.aira.integration.jira.JiraConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Processes Jira attachments:
 * - Images → OCR via Ollama vision model (extracts text from screenshots/error images)
 * - Text/CSV files → direct text extraction
 * - Other files → metadata extraction (filename, size, type)
 */
@Service
public class AttachmentProcessor {

    private static final Logger log = LoggerFactory.getLogger(AttachmentProcessor.class);
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB max for vision
    private static final long MAX_TEXT_SIZE = 1 * 1024 * 1024;   // 1MB max for text files

    private final JiraConnector jiraConnector;
    private final WebClient ollamaClient;
    private final String visionModel;
    private final int timeoutSeconds;

    public AttachmentProcessor(
            JiraConnector jiraConnector,
            @Value("${ollama.url:http://localhost:11434}") String ollamaUrl,
            @Value("${ollama.vision-model:llava:7b}") String visionModel,
            @Value("${ollama.timeout-seconds:120}") int timeoutSeconds) {
        this.jiraConnector = jiraConnector;
        this.ollamaClient = WebClient.builder().baseUrl(ollamaUrl).build();
        this.visionModel = visionModel;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Process all attachments from a Jira issue.
     * Returns extracted text content from images (OCR) and text files.
     */
    public List<AttachmentContent> processAttachments(List<JiraAttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<AttachmentContent> results = new ArrayList<>();

        for (JiraAttachmentDto attachment : attachments) {
            try {
                if (attachment.isImage() && attachment.size() <= MAX_IMAGE_SIZE) {
                    processImage(attachment).ifPresent(results::add);
                } else if (attachment.isTextBased() && attachment.size() <= MAX_TEXT_SIZE) {
                    processTextFile(attachment).ifPresent(results::add);
                } else {
                    // Extract metadata-only content for other file types
                    results.add(new AttachmentContent(
                            attachment.filename(),
                            attachment.mimeType(),
                            "Attachment: " + attachment.filename() + " (" + formatSize(attachment.size()) + ")",
                            "metadata"
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to process attachment {}: {}", attachment.filename(), e.getMessage());
            }
        }

        log.info("Processed {} attachments, extracted content from {} files",
                attachments.size(), results.size());
        return results;
    }

    /**
     * Downloads an image and runs OCR via Ollama vision model.
     */
    @SuppressWarnings("unchecked")
    private Optional<AttachmentContent> processImage(JiraAttachmentDto attachment) {
        log.debug("Processing image attachment: {} ({})", attachment.filename(), formatSize(attachment.size()));

        Optional<byte[]> imageBytes = jiraConnector.downloadAttachment(attachment.contentUrl());
        if (imageBytes.isEmpty()) {
            log.warn("Could not download image: {}", attachment.filename());
            return Optional.empty();
        }

        // Encode image to base64 for Ollama vision API
        String base64Image = Base64.getEncoder().encodeToString(imageBytes.get());

        // Call Ollama vision model for OCR
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", visionModel,
                    "prompt", "Extract ALL text visible in this image. If it's an error screenshot, extract the error message, stack trace, and any relevant details. If it's a table or data, extract the data in a structured format. Return only the extracted text, no commentary.",
                    "images", List.of(base64Image),
                    "stream", false
            );

            Map<String, Object> response = ollamaClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null && response.get("response") != null) {
                String extractedText = (String) response.get("response");
                if (extractedText != null && !extractedText.isBlank()) {
                    log.info("OCR extracted {} chars from image: {}", extractedText.length(), attachment.filename());
                    return Optional.of(new AttachmentContent(
                            attachment.filename(),
                            attachment.mimeType(),
                            extractedText.trim(),
                            "ocr"
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Vision model OCR failed for {} (model: {}): {}",
                    attachment.filename(), visionModel, e.getMessage());
        }

        // Fallback: return filename as context (even without OCR, filename gives clues)
        return Optional.of(new AttachmentContent(
                attachment.filename(),
                attachment.mimeType(),
                "Image attachment: " + attachment.filename() + " (OCR unavailable - vision model not loaded)",
                "metadata"
        ));
    }

    /**
     * Downloads and reads text-based files (CSV, TXT, JSON, XML, logs).
     */
    private Optional<AttachmentContent> processTextFile(JiraAttachmentDto attachment) {
        log.debug("Processing text attachment: {} ({})", attachment.filename(), formatSize(attachment.size()));

        Optional<byte[]> fileBytes = jiraConnector.downloadAttachment(attachment.contentUrl());
        if (fileBytes.isEmpty()) {
            return Optional.empty();
        }

        String content = new String(fileBytes.get(), StandardCharsets.UTF_8);
        // Truncate to reasonable size for knowledge store
        if (content.length() > 3000) {
            content = content.substring(0, 3000) + "\n... [truncated]";
        }

        log.info("Extracted {} chars from text file: {}", content.length(), attachment.filename());
        return Optional.of(new AttachmentContent(
                attachment.filename(),
                attachment.mimeType(),
                content,
                "text"
        ));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }

    /**
     * Represents extracted content from an attachment.
     */
    public record AttachmentContent(
            String filename,
            String mimeType,
            String extractedText,
            String extractionMethod  // "ocr", "text", "metadata"
    ) {}
}
