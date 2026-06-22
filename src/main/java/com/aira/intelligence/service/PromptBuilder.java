package com.aira.intelligence.service;

import com.aira.common.dto.RetrievedContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    private static final int MAX_PROMPT_CHARS = 8000;

    /**
     * Build investigation prompt — focuses on hypothesis and identifying missing info,
     * rather than confidently declaring a root cause without sufficient evidence.
     */
    public String buildInvestigationPrompt(String serviceName, String summary, String exceptionType,
                                            List<RetrievedContext> context,
                                            String confluenceContext, String logContext,
                                            String codeContext, String rcaComment) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an incident investigator. Analyze this incident and produce an INVESTIGATION REPORT.\n");
        sb.append("IMPORTANT RULES:\n");
        sb.append("- Do NOT make up a root cause if evidence is insufficient.\n");
        sb.append("- If you lack server logs, stack traces, or deployment info, say what is MISSING.\n");
        sb.append("- State your hypothesis clearly as a HYPOTHESIS, not as a confirmed fact.\n");
        sb.append("- Ask specific questions that would help confirm or deny the hypothesis.\n");
        sb.append("- Suggest concrete next investigation steps.\n\n");

        // Section 1: Incident
        sb.append("INCIDENT:\n");
        sb.append("- Service: ").append(serviceName != null ? serviceName : "unknown").append("\n");
        sb.append("- Summary: ").append(summary != null ? summary : "No summary").append("\n");
        if (exceptionType != null && !exceptionType.isBlank()) {
            sb.append("- Exception: ").append(exceptionType).append("\n");
        }
        sb.append("\n");

        // Section 2: Existing investigation notes
        if (rcaComment != null && !rcaComment.isBlank()) {
            sb.append("ENGINEER NOTES (already investigated):\n");
            String truncated = rcaComment.length() > 600 ? rcaComment.substring(0, 600) + "..." : rcaComment;
            sb.append(truncated).append("\n\n");
        }

        // Section 3: Source code
        if (codeContext != null && !codeContext.isBlank()) {
            sb.append("SOURCE CODE:\n");
            sb.append(codeContext).append("\n\n");
        }

        // Section 4: Server logs
        if (logContext != null && !logContext.isBlank()) {
            sb.append("SERVER LOGS:\n");
            sb.append(logContext).append("\n\n");
        }

        // Section 5: Similar past incidents
        if (context != null && !context.isEmpty()) {
            sb.append("SIMILAR PAST INCIDENTS:\n");
            for (int i = 0; i < Math.min(context.size(), 2); i++) {
                RetrievedContext ctx = context.get(i);
                String snippet = ctx.snippet() != null ? ctx.snippet() : "No details";
                if (snippet.length() > 150) snippet = snippet.substring(0, 150) + "...";
                sb.append(i + 1).append(". [").append(ctx.referenceId()).append("] ").append(snippet).append("\n");
            }
            sb.append("\n");
        }

        // Section 6: Documentation
        if (confluenceContext != null && !confluenceContext.isBlank()) {
            sb.append("DOCUMENTATION:\n");
            sb.append(confluenceContext).append("\n\n");
        }

        // Section 7: Response format
        sb.append("RESPOND IN THIS EXACT JSON FORMAT:\n");
        sb.append("{\n");
        sb.append("  \"hypothesis\": \"your best hypothesis based on available evidence (state clearly this is a hypothesis)\",\n");
        sb.append("  \"confidence\": 0-100,\n");
        sb.append("  \"severity\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n");
        sb.append("  \"evidenceFound\": [\"what evidence supports this hypothesis\"],\n");
        sb.append("  \"missingInfo\": [\"what information is needed to confirm — be specific\"],\n");
        sb.append("  \"questionsToAsk\": [\"specific questions for the engineer or team\"],\n");
        sb.append("  \"nextSteps\": [\"concrete investigation actions to take next\"],\n");
        sb.append("  \"recommendations\": {\n");
        sb.append("    \"immediate\": [\"action if hypothesis is correct\"],\n");
        sb.append("    \"shortTerm\": [\"action1\"],\n");
        sb.append("    \"longTerm\": [\"action1\"]\n");
        sb.append("  },\n");
        sb.append("  \"summary\": \"one-line investigation status\"\n");
        sb.append("}\n");

        String prompt = sb.toString();
        if (prompt.length() > MAX_PROMPT_CHARS) {
            prompt = prompt.substring(0, MAX_PROMPT_CHARS);
        }
        return prompt;
    }

    /**
     * Backward-compatible overloads.
     */
    public String buildRcaPrompt(String serviceName, String summary, String exceptionType,
                                  List<RetrievedContext> context,
                                  String confluenceContext, String logContext,
                                  String codeContext, String rcaComment) {
        return buildInvestigationPrompt(serviceName, summary, exceptionType, context,
                confluenceContext, logContext, codeContext, rcaComment);
    }

    public String buildRcaPrompt(String serviceName, String summary, String exceptionType,
                                  List<RetrievedContext> context,
                                  String confluenceContext, String logContext) {
        return buildInvestigationPrompt(serviceName, summary, exceptionType, context,
                confluenceContext, logContext, null, null);
    }

    public String buildRcaPrompt(String serviceName, String summary, String exceptionType,
                                  List<RetrievedContext> context) {
        return buildInvestigationPrompt(serviceName, summary, exceptionType, context,
                null, null, null, null);
    }
}
