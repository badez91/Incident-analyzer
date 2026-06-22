package com.aira.integration.logs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogReaderService logReaderService;

    public LogController(LogReaderService logReaderService) {
        this.logReaderService = logReaderService;
    }

    @GetMapping("/search")
    public ResponseEntity<LogReaderService.LogSearchResult> searchLogs(
            @RequestParam String service,
            @RequestParam(required = false) List<String> keywords,
            @RequestParam(defaultValue = "60") int minutesBack,
            @RequestParam(defaultValue = "50") int maxLines) {

        LogReaderService.LogSearchResult result = logReaderService.searchLogs(
                service, keywords, minutesBack, maxLines);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        boolean enabled = logReaderService.isEnabled();
        return ResponseEntity.ok("{\"enabled\":" + enabled + "}");
    }
}
