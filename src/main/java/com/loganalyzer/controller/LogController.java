package com.loganalyzer.controller;

import com.loganalyzer.model.AiAnalysis;
import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.service.LogParserService;
import com.loganalyzer.service.OllamaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
public class LogController {

    private final LogParserService logParserService;
    private final OllamaService ollamaService;
    private final List<String> availableModels;
    private final String defaultModel;

    public LogController(LogParserService logParserService,
                         OllamaService ollamaService,
                         @Value("${ollama.model}") String defaultModel,
                         @Value("${ollama.models}") List<String> availableModels) {
        this.logParserService = logParserService;
        this.ollamaService = ollamaService;
        this.defaultModel = defaultModel;
        this.availableModels = availableModels;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("models", availableModels);
        model.addAttribute("defaultModel", defaultModel);
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "model", required = false) String selectedModel,
                          Model model) {
        // Always pass models for error cases that return to index
        model.addAttribute("models", availableModels);
        model.addAttribute("defaultModel", defaultModel);

        // Validate file
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".log")) {
            model.addAttribute("error", "Please upload a .log file.");
            return "index";
        }

        if (file.isEmpty()) {
            model.addAttribute("error", "The uploaded file is empty.");
            return "index";
        }

        // Read file content
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            model.addAttribute("error", "Failed to read the uploaded file: " + e.getMessage());
            return "index";
        }

        // Phase 1: parse and count exceptions
        Map<String, Integer> exceptionCounts = logParserService.parseLog(content);

        if (exceptionCounts.isEmpty()) {
            model.addAttribute("error", "No exceptions or errors found in the log file.");
            return "index";
        }

        // Phase 2: build enriched summary and call Ollama
        String summary = logParserService.buildEnrichedSummary(content);

        // Use selected model or fall back to default
        String modelToUse = (selectedModel != null && !selectedModel.isBlank())
                ? selectedModel : defaultModel;
        AiAnalysis aiAnalysis = ollamaService.getStructuredAnalysis(summary, modelToUse);

        // Build result
        AnalysisResult result = new AnalysisResult(filename, exceptionCounts, summary, null);
        model.addAttribute("result", result);
        model.addAttribute("aiAnalysis", aiAnalysis);
        model.addAttribute("selectedModel", modelToUse);

        return "results";
    }
}
