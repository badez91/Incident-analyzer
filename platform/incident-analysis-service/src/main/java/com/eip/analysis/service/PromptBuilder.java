package com.eip.analysis.service;

import com.eip.common.model.CanonicalIncidentEvent;
import com.eip.common.model.ScoredMatch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    private static final int MAX_HISTORY_CHARS = 8000;

    public String buildPrompt(CanonicalIncidentEvent incident, List<ScoredMatch> similar) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert incident analysis AI. Analyze the following incident and provide a structured JSON response.\n\n");
        sb.append("## Incident Details\n");
        sb.append("- Service: ").append(incident.serviceName()).append("\n");
        sb.append("- Severity: ").append(incident.severity() != null ? incident.severity() : "UNKNOWN").append("\n");
        sb.append("- Total Events: ").append(incident.totalEvents()).append("\n");
        sb.append("- Total Exceptions: ").append(incident.totalExceptions()).append("\n");

        if (incident.exceptionTypes() != null && !incident.exceptionTypes().isEmpty()) {
            sb.append("- Exception Types: ").append(String.join(", ", incident.exceptionTypes())).append("\n");
        }

        if (incident.symptoms() != null && !incident.symptoms().isEmpty()) {
            sb.append("- Symptoms: ").append(String.join("; ", incident.symptoms())).append("\n");
        }

        if (incident.exceptionCounts() != null && !incident.exceptionCounts().isEmpty()) {
            sb.append("- Exception Counts: ").append(incident.exceptionCounts()).append("\n");
        }

        if (incident.events() != null && !incident.events().isEmpty()) {
            sb.append("\n## Log Events (sample)\n");
            int maxEvents = Math.min(incident.events().size(), 10);
            for (int i = 0; i < maxEvents; i++) {
                var event = incident.events().get(i);
                sb.append(String.format("  [%s] %s: %s\n", event.level(), event.eventType(), event.message()));
            }
        }

        // Add historical context with character cap
        if (similar != null && !similar.isEmpty()) {
            sb.append("\n## Historical Context (Similar Past Incidents)\n");
            StringBuilder historySb = new StringBuilder();
            for (ScoredMatch match : similar) {
                String entry = String.format("- Incident %s (service: %s, similarity: %.2f): Root Cause: %s\n",
                        match.incidentId(), match.service(), match.similarityScore(),
                        match.rootCause() != null ? match.rootCause() : "Unknown");
                if (historySb.length() + entry.length() > MAX_HISTORY_CHARS) {
                    break;
                }
                historySb.append(entry);
            }
            sb.append(historySb);
        }

        sb.append("\n## Required Response Format (JSON)\n");
        sb.append("Respond with ONLY a JSON object containing:\n");
        sb.append("{\n");
        sb.append("  \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n");
        sb.append("  \"rootCause\": \"detailed root cause analysis\",\n");
        sb.append("  \"confidence\": \"high|medium|low\",\n");
        sb.append("  \"confidencePercent\": 0-100,\n");
        sb.append("  \"businessImpact\": [\"impact1\", \"impact2\"],\n");
        sb.append("  \"recommendations\": {\n");
        sb.append("    \"immediate\": [\"action1\"],\n");
        sb.append("    \"shortTerm\": [\"action1\"],\n");
        sb.append("    \"longTerm\": [\"action1\"]\n");
        sb.append("  },\n");
        sb.append("  \"summary\": \"brief one-line summary\"\n");
        sb.append("}\n");

        return sb.toString();
    }
}
