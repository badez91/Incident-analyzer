package com.loganalyzer.model;

import java.util.Map;

/**
 * Holds the results of a log analysis.
 */
public class AnalysisResult {

    private final String filename;
    private final Map<String, Integer> exceptionCounts;
    private final String summary;
    private final String aiResponse;

    public AnalysisResult(String filename, Map<String, Integer> exceptionCounts,
                          String summary, String aiResponse) {
        this.filename = filename;
        this.exceptionCounts = exceptionCounts;
        this.summary = summary;
        this.aiResponse = aiResponse;
    }

    public String getFilename() {
        return filename;
    }

    public Map<String, Integer> getExceptionCounts() {
        return exceptionCounts;
    }

    public String getSummary() {
        return summary;
    }

    public String getAiResponse() {
        return aiResponse;
    }
}
