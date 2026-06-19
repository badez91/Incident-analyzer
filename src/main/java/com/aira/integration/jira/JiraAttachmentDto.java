package com.aira.integration.jira;

/**
 * Represents a Jira issue attachment.
 */
public record JiraAttachmentDto(
    String id,
    String filename,
    String mimeType,
    long size,
    String contentUrl,
    String created
) {
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public boolean isTextBased() {
        return mimeType != null && (
            mimeType.contains("text") ||
            mimeType.contains("csv") ||
            mimeType.contains("json") ||
            mimeType.contains("xml")
        );
    }
}
