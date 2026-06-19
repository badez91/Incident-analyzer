package com.aira.intelligence.service;

import com.aira.common.dto.RetrievedContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    private static final int MAX_PROMPT_CHARS = 8000; // ~2000 tokens

    public String buildRcaPrompt(String serviceName, String summary, String exceptionType,
                                  List<RetrievedContext> context) {
        StringBuilder sb = new StringBuilder();

        // Section 1: Incident description (~200 tokens)
        sb.append("Analyze this production incident and provide root cause analysis.\n\n");
        sb.append("INCIDENT DETAILS:\n");
        sb.append("- Service: ").append(serviceName != null ? serviceName : "unknown").append("\n");
        sb.append("- Summary: ").append(summary != null ? summary : "No summary provided").append("\n");
        if (exceptionType != null && !exceptionType.isBlank()) {
            sb.append("- Exception: ").append(exceptionType).append("\n");
        }
        sb.append("\n");

        // Section 2: Retrieved context (~800 tokens)
        if (context != null && !context.isEmpty()) {
            sb.append("SIMILAR PAST INCIDENTS:\n");
            for (int i = 0; i < Math.min(context.size(), 5); i++) {
                RetrievedContext ctx = context.get(i);
                String snippet = ctx.snippet() != null ? ctx.snippet() : "No details";
                // Each snippet max 150 chars
                if (snippet.length() > 150) {
                    snippet = snippet.substring(0, 150) + "...";
                }
                sb.append(i + 1).append(". [").append(ctx.referenceId()).append("] ").append(snippet).append("\n");
            }
            sb.append("\n");
        }

        // Section 3: Response format instruction (~200 tokens)
        sb.append("RESPOND IN THIS JSON FORMAT:\n");
        sb.append("{\n");
        sb.append("  \"rootCause\": \"brief explanation of the most likely root cause\",\n");
        sb.append("  \"confidence\": 0-100,\n");
        sb.append("  \"severity\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n");
        sb.append("  \"businessImpact\": [\"impact1\", \"impact2\"],\n");
        sb.append("  \"recommendations\": {\n");
        sb.append("    \"immediate\": [\"action1\"],\n");
        sb.append("    \"shortTerm\": [\"action1\"],\n");
        sb.append("    \"longTerm\": [\"action1\"]\n");
        sb.append("  },\n");
        sb.append("  \"summary\": \"one-line summary of the analysis\"\n");
        sb.append("}\n");

        String prompt = sb.toString();

        // Enforce max size
        if (prompt.length() > MAX_PROMPT_CHARS) {
            prompt = prompt.substring(0, MAX_PROMPT_CHARS);
        }

        return prompt;
    }
}
