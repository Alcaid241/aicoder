package com.ai.coder.chat.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmitSkillDraftToolTest {

    private RestTemplate restTemplate;
    private SubmitSkillDraftTool tool;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        tool = new SubmitSkillDraftTool(restTemplate);
    }

    @Test
    void submitSkillDraft_success_returns_status_and_propagates_user_header() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenReturn(Map.of("name", "code-review", "status", "PENDING_APPROVAL"));
        ToolContext ctx = new ToolContext(Map.<String, Object>of("userId", 7L));

        String result = tool.submitSkillDraft("code-review", "审查代码",
                "---\nname: code-review\ndescription: 审查。\n---\n正文足够长以满足最小长度要求。", ctx);

        assertTrue(result.contains("code-review"), result);
        assertTrue(result.contains("PENDING_APPROVAL"), "应在返回里告知状态：" + result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(any(String.class), captor.capture(), eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertTrue(headers.containsKey("X-User-Id"), "应透传 X-User-Id");
        assertEquals("7", headers.getFirst("X-User-Id"), "X-User-Id 头应为 7");
    }

    @Test
    void submitSkillDraft_rest_error_returns_friendly_string_without_throwing() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("skill 服务不可达"));
        ToolContext ctx = new ToolContext(Map.<String, Object>of("userId", 7L));

        String result = assertDoesNotThrow(() ->
                tool.submitSkillDraft("code-review", "审查代码", "正文", ctx));

        assertTrue(result.contains("提交失败"), "异常应降级为友好串：" + result);
    }

    @Test
    void submitSkillDraft_rejects_invalid_name_without_calling_rest() {
        ToolContext ctx = new ToolContext(Map.<String, Object>of("userId", 7L));

        String result = tool.submitSkillDraft("../etc/passwd", "审查代码", "正文", ctx);

        assertTrue(result.contains("非法"), "非法名应本地拒绝：" + result);
        verify(restTemplate, never()).postForObject(any(String.class), any(), any());
    }

}
