package com.aira.integration.confluence;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/confluence")
public class ConfluenceController {

    private final ConfluenceConnector confluenceConnector;

    public ConfluenceController(ConfluenceConnector confluenceConnector) {
        this.confluenceConnector = confluenceConnector;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ConfluencePageDto>> search(
            @RequestParam List<String> keywords,
            @RequestParam(defaultValue = "3") int maxResults) {

        List<ConfluencePageDto> pages = confluenceConnector.searchPages(keywords, maxResults);
        return ResponseEntity.ok(pages);
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        boolean enabled = confluenceConnector.isEnabled();
        return ResponseEntity.ok("{\"enabled\":" + enabled + "}");
    }
}
