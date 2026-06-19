package com.aira.document.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    private ContentExtractor contentExtractor;

    @BeforeEach
    void setUp() {
        contentExtractor = new ContentExtractor();
    }

    @Test
    void extractExceptionTypes_findsJavaExceptions() {
        String text = "Caused by NullPointerException and SocketTimeoutException in production";
        List<String> result = contentExtractor.extractExceptionTypes(text);

        assertThat(result).containsExactly("NullPointerException", "SocketTimeoutException");
    }

    @Test
    void extractExceptionTypes_returnsEmptyForNullInput() {
        assertThat(contentExtractor.extractExceptionTypes(null)).isEmpty();
        assertThat(contentExtractor.extractExceptionTypes("")).isEmpty();
    }

    @Test
    void extractExceptionTypes_deduplicates() {
        String text = "NullPointerException thrown then NullPointerException again";
        List<String> result = contentExtractor.extractExceptionTypes(text);

        assertThat(result).hasSize(1).contains("NullPointerException");
    }

    @Test
    void extractErrorBehaviors_findsKeywords() {
        String text = "Service timeout after connection refused during payment processing";
        List<String> result = contentExtractor.extractErrorBehaviors(text);

        assertThat(result).contains("timeout", "connection refused");
    }

    @Test
    void extractComponents_findsComponentKeywords() {
        String text = "The batch scheduler failed during database operation";
        List<String> result = contentExtractor.extractComponents(text);

        assertThat(result).contains("batch", "scheduler", "database");
    }

    @Test
    void extractServiceName_extractsPrefix() {
        assertThat(contentExtractor.extractServiceName("CM-5553")).isEqualTo("cm");
        assertThat(contentExtractor.extractServiceName("PAY-123")).isEqualTo("pay");
    }

    @Test
    void extractServiceName_handlesEdgeCases() {
        assertThat(contentExtractor.extractServiceName(null)).isEqualTo("unknown");
        assertThat(contentExtractor.extractServiceName("")).isEqualTo("unknown");
        assertThat(contentExtractor.extractServiceName("NOPREFIX")).isEqualTo("noprefix");
    }

    @Test
    void buildSearchableText_combinesFields() {
        String result = contentExtractor.buildSearchableText(
                "Summary here", "Description here", List.of("Comment 1", "Comment 2"));

        assertThat(result).contains("Summary here", "Description here", "Comment 1");
    }

    @Test
    void buildSearchableText_truncatesLongText() {
        String longDescription = "x".repeat(3000);
        String result = contentExtractor.buildSearchableText("summary", longDescription, List.of());

        assertThat(result.length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void buildSummary_createsCompactSummary() {
        String result = contentExtractor.buildSummary(
                "Service Down", List.of("TimeoutException"), List.of("timeout", "unavailable"));

        assertThat(result).contains("Service Down");
        assertThat(result).contains("TimeoutException");
        assertThat(result).contains("timeout");
    }
}
