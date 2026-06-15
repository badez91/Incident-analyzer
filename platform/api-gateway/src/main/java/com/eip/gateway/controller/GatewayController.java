package com.eip.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final Map<String, String> routeMapping;
    private final WebClient defaultWebClient;
    private final WebClient analysisWebClient;

    public GatewayController(
            Map<String, String> routeMapping,
            @Qualifier("defaultGatewayWebClient") WebClient defaultWebClient,
            @Qualifier("analysisGatewayWebClient") WebClient analysisWebClient) {
        this.routeMapping = routeMapping;
        this.defaultWebClient = defaultWebClient;
        this.analysisWebClient = analysisWebClient;
    }

    @RequestMapping(value = "/api/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<String> proxy(HttpServletRequest request, @RequestBody(required = false) String body) {
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = queryString != null ? path + "?" + queryString : path;

        String targetBaseUrl = resolveTarget(path);
        if (targetBaseUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"No route found for path: " + path + "\"}");
        }

        String targetUrl = targetBaseUrl + fullPath;
        WebClient client = path.startsWith("/api/analysis") ? analysisWebClient : defaultWebClient;

        log.debug("Proxying {} {} -> {}", request.getMethod(), path, targetUrl);

        try {
            WebClient.RequestBodySpec requestSpec = client
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(targetUrl);

            // Forward content-type if present
            String contentType = request.getContentType();
            if (contentType != null) {
                requestSpec.contentType(MediaType.parseMediaType(contentType));
            }

            WebClient.ResponseSpec responseSpec;
            if (body != null && !body.isEmpty()) {
                responseSpec = requestSpec.bodyValue(body).retrieve();
            } else {
                responseSpec = requestSpec.retrieve();
            }

            String responseBody = responseSpec.bodyToMono(String.class).block();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseBody);

        } catch (WebClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to proxy request to {}: {}", targetUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Backend service unavailable\",\"target\":\"" + targetBaseUrl + "\",\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private String resolveTarget(String path) {
        for (Map.Entry<String, String> entry : routeMapping.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
