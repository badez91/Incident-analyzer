package com.aira.intelligence.service;

import com.aira.common.dto.InvestigationResult;
import com.aira.common.dto.RcaResult;
import com.aira.common.dto.RetrievedContext;
import com.aira.document.service.ContentExtractor;
import com.aira.integration.confluence.ConfluenceConnector;
import com.aira.integration.confluence.ConfluencePageDto;
import com.aira.integration.logs.LogReaderService;
import com.aira.integration.sourcecode.SourceCodeService;
import com.aira.knowledge.entity.EngineeringDocumentEntity;
import com.aira.knowledge.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RcaEngine {

    private static final Logger log = LoggerFactory.getLogger(RcaEngine.class);

    private final KnowledgeService knowledgeService;
    private final PromptBuilder promptBuilder;
    private final OllamaClient ollamaClient;
    private final ResponseParser responseParser;
    private final ConfluenceConnector confluenceConnector;
    private final LogReaderService logReaderService;
    private final SourceCodeService sourceCodeService;
    private final ContentExtractor contentExtractor;

    @Value("${ollama.model:qwen3:1.7b}")
    private String model;

    public RcaEngine(KnowledgeService knowledgeService,
                     PromptBuilder promptBuilder,
                     OllamaClient ollamaClient,
                     ResponseParser responseParser,
                     ConfluenceConnector confluenceConnector,
                     LogReaderService logReaderService,
                     SourceCodeService sourceCodeService,
                     ContentExtractor contentExtractor) {
        this.knowledgeService = knowledgeService;
        this.promptBuilder = promptBuilder;
        this.ollamaClient = ollamaClient;
        this.responseParser = responseParser;
        this.confluenceConnector = confluenceConnector;
        this.logReaderService = logReaderService;
        this.sourceCodeService = sourceCodeService;
        this.contentExtractor = contentExtractor;
    }

    /**
     * Run investigation (not definitive RCA) with full context pipeline.
     * Returns InvestigationResult with hypothesis, missing info, and questions to ask.
     */
    public InvestigationResult investigate(UUID incidentId, String serviceName, String summary,
                                            String exceptionType, List<String> components, String excludeRef,
                                            String fullTicketText, List<String> comments) {
        log.info("Starting investigation: incidentId={}, service={}, exception={}, excludeRef={}",
                incidentId, serviceName, exceptionType, excludeRef);

        long startTime = System.currentTimeMillis();

        // Step 0: Check for existing engineer RCA comment
        String rcaComment = contentExtractor.extractRcaComment(comments);
        if (rcaComment != null) {
            log.info("Detected existing investigation notes ({} chars)", rcaComment.length());
        }

        // Step 1: Knowledge base context
        List<EngineeringDocumentEntity> docs = knowledgeService.findSimilar(
                summary, exceptionType, components, 2, excludeRef);

        List<RetrievedContext> context = docs.stream()
                .map(doc -> new RetrievedContext(
                        doc.getId(), doc.getSourceType(), doc.getReferenceId(),
                        truncate(doc.getSearchableText(), 150), 1.0))
                .toList();

        // Step 2: Confluence docs
        String confluenceContext = fetchConfluenceContext(serviceName, exceptionType, components);

        // Step 3: Server logs
        String logContext = fetchLogContext(serviceName, exceptionType, components);

        // Step 4: Source code context
        String codeContext = fetchCodeContext(fullTicketText);

        log.debug("Context: knowledgeDocs={}, confluence={}, logs={}, code={}, rcaComment={}",
                context.size(),
                confluenceContext != null ? confluenceContext.length() : 0,
                logContext != null ? logContext.length() : 0,
                codeContext != null ? codeContext.length() : 0,
                rcaComment != null ? "yes" : "no");

        // Step 5: Build investigation prompt
        String prompt = promptBuilder.buildInvestigationPrompt(
                serviceName, summary, exceptionType, context,
                confluenceContext, logContext, codeContext, rcaComment);

        // Step 6: Call Ollama
        String rawResponse = ollamaClient.analyze(prompt, model)
                .orElse(null);

        long inferenceMs = System.currentTimeMillis() - startTime;

        // Step 7: Parse investigation response
        InvestigationResult result = responseParser.parseInvestigationResponse(
                rawResponse, incidentId, context, inferenceMs);

        log.info("Investigation complete: incidentId={}, status={}, confidence={}%, inferenceMs={}",
                incidentId, result.status(), result.confidencePercent(), inferenceMs);

        return result;
    }

    /**
     * Backward-compatible overload without excludeRef.
     */
    public RcaResult analyze(UUID incidentId, String serviceName, String summary,
                             String exceptionType, List<String> components) {
        InvestigationResult inv = investigate(incidentId, serviceName, summary,
                exceptionType, components, null, null, null);
        return new RcaResult(inv.incidentId(), inv.severity(), inv.hypothesis(),
                inv.confidencePercent(), inv.evidenceFound(), inv.recommendations(),
                inv.summary(), inv.contextUsed(), inv.tokensUsed(), inv.inferenceTimeMs(), inv.analyzedAt());
    }

    /**
     * Fetch relevant Confluence documentation as context.
     */
    private String fetchConfluenceContext(String serviceName, String exceptionType, List<String> components) {
        if (!confluenceConnector.isEnabled()) return null;

        try {
            List<String> keywords = new ArrayList<>();
            if (serviceName != null && !serviceName.isBlank()) keywords.add(serviceName);
            if (exceptionType != null && !exceptionType.isBlank()) keywords.add(exceptionType);
            if (components != null) keywords.addAll(components.stream().limit(2).toList());

            if (keywords.isEmpty()) return null;

            List<ConfluencePageDto> pages = confluenceConnector.searchPages(keywords, 2);

            if (pages.isEmpty()) return null;

            // Combine page snippets (max 400 chars total)
            StringBuilder sb = new StringBuilder();
            for (ConfluencePageDto page : pages) {
                String snippet = page.toContextSnippet();
                if (sb.length() + snippet.length() > 400) break;
                sb.append(snippet).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("Confluence context fetch failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch relevant source code as context (from stack traces in ticket).
     */
    private String fetchCodeContext(String ticketText) {
        if (!sourceCodeService.isEnabled() || ticketText == null) return null;

        try {
            return sourceCodeService.extractCodeContext(ticketText);
        } catch (Exception e) {
            log.debug("Source code context fetch failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch relevant server logs as context.
     */
    private String fetchLogContext(String serviceName, String exceptionType, List<String> components) {
        if (!logReaderService.isEnabled()) return null;

        try {
            List<String> keywords = new ArrayList<>();
            if (exceptionType != null && !exceptionType.isBlank()) keywords.add(exceptionType);
            if (components != null) keywords.addAll(components.stream().limit(2).toList());

            LogReaderService.LogSearchResult logResult = logReaderService.searchLogs(
                    serviceName != null ? serviceName : "unknown",
                    keywords,
                    60,  // last 60 minutes
                    20   // max 20 lines
            );

            if (!logResult.hasContent()) return null;

            return logResult.toContextSnippet(400);
        } catch (Exception e) {
            log.debug("Log context fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
