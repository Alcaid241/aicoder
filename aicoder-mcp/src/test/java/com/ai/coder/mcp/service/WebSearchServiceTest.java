package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSearchServiceTest {

    private RestTemplate restTemplate;
    private WebSearchService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        McpProperties props = new McpProperties();
        props.getSearch().setApiKey("k");
        service = new WebSearchService(restTemplate, props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returns_formatted_results() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("results", List.of(
                        Map.of("title", "Spring AI", "url", "https://spring.io", "content", "AI 框架"))));

        String out = service.search("spring ai");

        assertTrue(out.contains("Spring AI"), out);
        assertTrue(out.contains("https://spring.io"), out);
    }

    @Test
    void search_without_api_key_returns_hint() {
        McpProperties props = new McpProperties();
        props.getSearch().setApiKey("");
        WebSearchService s = new WebSearchService(restTemplate, props);

        String out = s.search("anything");
        assertTrue(out.contains("未配置"), out);
    }
}
