package com.aira.intelligence.service;

import com.aira.common.dto.RcaResult;
import com.aira.common.dto.RetrievedContext;
import com.aira.knowledge.entity.EngineeringDocumentEntity;
import com.aira.knowledge.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RcaEngine {

    private static final Logger log = LoggerFactory.getLogger(RcaEngine.class);

    private final KnowledgeService knowledgeService;
    private final PromptBuilder promptBuilder;
    private final OllamaClient ollamaClient;
    private final ResponseParser responseParser;

    @Value("${ollama.model:qwen3:1.7b}")
    private String model;

    public RcaEngine(KnowledgeService knowledgeService,
                     PromptBuilder promptBuilder,
                     OllamaClient ollamaClient,
                     ResponseParser responseParser) {
        this.knowledgeService = knowledgeService;
        this.promptBuilder = promptBuilder;
        this.ollamaClient = ollamaClient;
        this.responseParser = responseParser;
    }

    public RcaResult analyze(UUID incidentId, String serviceName, String summary,
                             String exceptionType, List<String> components) {
        log.info("Starting RCA analysis: incidentId={}, service={}, exception={}",
                incidentId, serviceName, exceptionType);

        long startTime = System.currentTimeMillis();

        // Step 1: Retrieve context from Knowledge base
        List<EngineeringDocumentEntity> docs = knowledgeService.hybridSearch(
                serviceName, exceptionType, components, 5);

        List<RetrievedContext> context = docs.stream()
                .map(doc -> new RetrievedContext(
                        doc.getId(),
                        doc.getSourceType(),
                        doc.getReferenceId(),
                        truncate(doc.getSearchableText(), 150),
                        1.0  // placeholder similarity until vector search is active
                ))
                .toList();

        log.debug("Retrieved {} context documents for RCA", context.size());

        // Step 2: Build compact prompt
        String prompt = promptBuilder.buildRcaPrompt(serviceName, summary, exceptionType, context);

        // Step 3: Call Ollama
        String rawResponse = ollamaClient.analyze(prompt, model)
                .orElse("Analysis unavailable — LLM did not respond");

        long inferenceMs = System.currentTimeMillis() - startTime;

        // Step 4: Parse response and return RcaResult
        RcaResult result = responseParser.parseRcaResponse(rawResponse, incidentId, context, inferenceMs);
        log.info("RCA complete: incidentId={}, confidence={}%, inferenceMs={}",
                incidentId, result.confidencePercent(), inferenceMs);

        return result;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
