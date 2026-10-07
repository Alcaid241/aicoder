package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebFetchServiceTest {

    private RestTemplate restTemplate;
    private WebFetchService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new WebFetchService(restTemplate, new McpProperties());
    }

    @Test
    void isAllowed_rejects_private_ip() {
        assertFalse(service.isAllowed("http://127.0.0.1:8080/x"));
        assertFalse(service.isAllowed("http://localhost/x"));
        assertFalse(service.isAllowed("http://192.168.1.1/x"));
    }

    @Test
    void isAllowed_accepts_public_host() {
        assertTrue(service.isAllowed("https://example.com/page"));
    }

    @Test
    void fetch_rejects_private_url_without_request() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fetch("http://127.0.0.1/secret"));
    }

    @Test
    void fetch_converts_html_to_markdown() {
        when(restTemplate.getForObject(eq("https://example.com/a"), eq(String.class)))
                .thenReturn("<html><body><h1>Title</h1><p>Hello</p></body></html>");

        String md = service.fetch("https://example.com/a");

        assertTrue(md.contains("Title"), "markdown 应含标题文本：" + md);
    }
}
