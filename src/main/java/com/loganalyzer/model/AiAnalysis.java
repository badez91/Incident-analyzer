package com.loganalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured AI analysis response from Ollama.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnalysis {

    private String severity;
    private String confidence;

    @JsonProperty("confidencePercent")
    private int confidencePercent;

    @JsonProperty("rootCause")
    private String rootCause;

    @JsonProperty("businessImpact")
    private List<String> businessImpact;

    @JsonProperty("recommendedActions")
    private RecommendedActions recommendedActions;

    private String summary;

    // Fallback: raw text if JSON parsing failed
    private String rawResponse;

    // Error message if Ollama call failed
    private String errorMessage;

    public AiAnalysis() {}

    public static AiAnalysis error(String message) {
        AiAnalysis a = new AiAnalysis();
        a.errorMessage = message;
        return a;
    }

    public boolean isError() {
        return errorMessage != null;
    }

    public boolean isRawFallback() {
        return rawResponse != null && rootCause == null;
    }

    public boolean isStructured() {
        return rootCause != null && !isError();
    }

    // --- Getters and Setters ---

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public int getConfidencePercent() { return confidencePercent; }
    public void setConfidencePercent(int confidencePercent) { this.confidencePercent = confidencePercent; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public List<String> getBusinessImpact() { return businessImpact; }
    public void setBusinessImpact(List<String> businessImpact) { this.businessImpact = businessImpact; }

    public RecommendedActions getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(RecommendedActions recommendedActions) { this.recommendedActions = recommendedActions; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /**
     * Nested object for categorized actions.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecommendedActions {
        private List<String> immediate;
        private List<String> shortTerm;
        private List<String> longTerm;

        public List<String> getImmediate() { return immediate; }
        public void setImmediate(List<String> immediate) { this.immediate = immediate; }

        public List<String> getShortTerm() { return shortTerm; }
        public void setShortTerm(List<String> shortTerm) { this.shortTerm = shortTerm; }

        public List<String> getLongTerm() { return longTerm; }
        public void setLongTerm(List<String> longTerm) { this.longTerm = longTerm; }
    }
}
