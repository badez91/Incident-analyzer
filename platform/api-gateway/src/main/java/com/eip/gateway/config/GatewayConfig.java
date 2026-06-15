package com.eip.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class GatewayConfig {

    @Value("${services.ingestion.url}")
    private String ingestionUrl;

    @Value("${services.transformer.url}")
    private String transformerUrl;

    @Value("${services.analysis.url}")
    private String analysisUrl;

    @Value("${services.knowledge-store.url}")
    private String knowledgeStoreUrl;

    @Value("${services.similarity.url}")
    private String similarityUrl;

    @Value("${services.simulator.url}")
    private String simulatorUrl;

    @Bean
    public Map<String, String> routeMapping() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/api/incidents", ingestionUrl);
        routes.put("/api/knowledge", knowledgeStoreUrl);
        routes.put("/api/similarity", similarityUrl);
        routes.put("/api/analysis", analysisUrl);
        routes.put("/api/simulate", simulatorUrl);
        routes.put("/api/transform", transformerUrl);
        return routes;
    }

    @Bean("defaultGatewayWebClient")
    public WebClient defaultWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean("analysisGatewayWebClient")
    public WebClient analysisWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(300));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
