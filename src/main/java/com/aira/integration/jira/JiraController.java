package com.aira.integration.jira;

import com.aira.common.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jira")
public class JiraController {

    private final JiraConnector jiraConnector;

    public JiraController(JiraConnector jiraConnector) {
        this.jiraConnector = jiraConnector;
    }

    @GetMapping("/{key}")
    public ResponseEntity<JiraIssueDto> getIssue(@PathVariable String key) {
        return jiraConnector.getIssue(key)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Jira issue", key));
    }

    @PostMapping("/poll")
    public ResponseEntity<List<JiraIssueDto>> searchIssues(
            @RequestParam String jql,
            @RequestParam(defaultValue = "10") int maxResults) {
        List<JiraIssueDto> results = jiraConnector.searchIssues(jql, maxResults);
        return ResponseEntity.ok(results);
    }
}
