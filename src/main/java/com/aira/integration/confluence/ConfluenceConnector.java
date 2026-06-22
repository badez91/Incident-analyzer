package com.aira.integration.confluence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Confluence connector — searches for related engineering docs (runbooks, troubleshooting guides).
 * Uses Confluence REST API v2 (Cloud) with CQL search.
 */
@Component
public class ConfluenceConnector {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceConnector.class);

    private final WebClient webClient;
    private final boolean enabled;
    private final String spaceKey;

    public ConfluenceConnector(
            @Value("${confluence.base-url:}") String baseUrl,
            @Value("${confluence.username:}") String username,
            @Value("${confluence.api-token:}") String apiToken,
            @Value("${confluence.space-key:}") String spaceKey) {

        this.spaceKey = spaceKey;

        if (apiToken == null || apiToken.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            log.info("Confluence not configured. Confluence integration is disabled.");
            this.enabled = false;
            this.webClient = null;
            return;
        }

        this.enabled = true;
        String credentials = username + ":" + apiToken;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encoded)
                .defaultHeader("Accept", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();

        log.info("Confluence connector initialized: baseUrl={}, space={}", baseUrl, spaceKey);
    }

    /**
     * Search Confluence for pages related to the given keywords.
     * Searches in title and body content using CQL.
     *
     * @param keywords   search terms (service name, exception, component)
     * @param maxResults max pages to return
     * @return list of matching page summaries
     */
    @SuppressWarnings("unchecked")
    public List<ConfluencePageDto> searchPages(List<String> keywords, int maxResults) {
        if (!enabled || keywords == null || keywords.isEmpty()) return List.of();

        String cql = buildCql(keywords);
        log.info("Searching Confluence: cql={}", cql);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/content/search")
                            .queryParam("cql", cql)
                            .queryParam("limit", maxResults)
                            .queryParam("expand", "body.view,metadata.labels")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null) return List.of();

            List<ConfluencePageDto> pages = results.stream()
                    .map(this::mapToDto)
                    .toList();

            log.info("Found {} Confluence pages for keywords: {}", pages.size(), keywords);
            return pages;
        } catch (Exception e) {
            log.warn("Confluence search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String buildCql(List<String> keywords) {
        String searchText = String.join(" ", keywords);

        StringBuilder cql = new StringBuilder();
        cql.append("type = page");

        if (spaceKey != null && !spaceKey.isBlank()) {
            cql.append(" AND space = \"").append(spaceKey).append("\"");
        }

        cql.append(" AND (text ~ \"").append(searchText).append("\"");
        cql.append(" OR title ~ \"").append(searchText).append("\")");
        cql.append(" ORDER BY lastModified DESC");

        return cql.toString();
    }

    @SuppressWarnings("unchecked")
    private ConfluencePageDto mapToDto(Map<String, Object> result) {
        String id = result.get("id") != null ? result.get("id").toString() : null;
        String title = (String) result.get("title");

        // Extract body text (strip HTML for searchable content)
        String bodyExcerpt = "";
        Map<String, Object> body = (Map<String, Object>) result.get("body");
        if (body != null) {
            Map<String, Object> view = (Map<String, Object>) body.get("view");
            if (view != null) {
                String html = (String) view.get("value");
                if (html != null) {
                    bodyExcerpt = stripHtml(html);
                    if (bodyExcerpt.length() > 500) {
                        bodyExcerpt = bodyExcerpt.substring(0, 500);
                    }
                }
            }
        }

        // Extract labels
        List<String> labels = new ArrayList<>();
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        if (metadata != null) {
            Map<String, Object> labelsObj = (Map<String, Object>) metadata.get("labels");
            if (labelsObj != null) {
                List<Map<String, Object>> labelResults = (List<Map<String, Object>>) labelsObj.get("results");
                if (labelResults != null) {
                    for (Map<String, Object> l : labelResults) {
                        labels.add((String) l.get("name"));
                    }
                }
            }
        }

        Map<String, Object> links = (Map<String, Object>) result.get("_links");
        String webUrl = links != null ? (String) links.get("webui") : null;

        return new ConfluencePageDto(id, title, bodyExcerpt, labels, webUrl);
    }

    private String stripHtml(String html) {
        return html
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
