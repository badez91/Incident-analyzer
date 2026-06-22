package com.aira.integration.jira;

import java.util.List;

public record JiraIssueDto(
    String key,
    String summary,
    String description,
    String issueType,
    String priority,
    String status,
    String assignee,
    List<String> labels,
    List<String> comments,
    List<JiraAttachmentDto> attachments,
    String created,
    String updated
) {}
