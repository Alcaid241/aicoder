package com.ai.coder.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryReloadNotifierTest {

    private RestTemplate restTemplate;
    private RegistryReloadNotifier notifier;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        notifier = new RegistryReloadNotifier(restTemplate);
    }

    @Test
    void reloadAll_posts_reload_to_chat_rag_workflow() {
        notifier.reloadAll();

        verify(restTemplate).postForObject(eq("http://aicoder-chat/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-rag/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-workflow/internal/registry/reload"), eq(null), eq(Object.class));
    }

    @Test
    void reloadAll_continues_when_one_service_fails() {
        // chat 抛异常 → 不传播，其余继续
        when(restTemplate.postForObject(eq("http://aicoder-chat/internal/registry/reload"), eq(null), eq(Object.class)))
                .thenThrow(new RuntimeException("chat 不可达"));

        notifier.reloadAll(); // 不应抛

        verify(restTemplate).postForObject(eq("http://aicoder-chat/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-rag/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-workflow/internal/registry/reload"), eq(null), eq(Object.class));
    }
}
