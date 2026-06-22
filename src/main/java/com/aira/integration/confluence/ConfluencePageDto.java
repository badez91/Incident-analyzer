package com.aira.integration.confluence;

import java.util.List;

/**
 * Represents a Confluence page summary for use as RAG context.
 */
public record ConfluencePageDto(
    String id,
    String title,
    String bodyExcerpt,
    List<String> labels,
    String webUrl
) {
    /**
     * Returns a compact representation suitable for LLM context.
     */
    public String toContextSnippet() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Doc: ").append(title).append("]");
        if (bodyExcerpt != null && !bodyExcerpt.isBlank()) {
            sb.append(" ").append(bodyExcerpt);
        }
        return sb.toString();
    }
}
