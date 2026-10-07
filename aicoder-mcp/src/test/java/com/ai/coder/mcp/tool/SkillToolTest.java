package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.config.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillToolTest {

    private RestTemplate restTemplate;
    private SkillTool tool;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        McpProperties props = new McpProperties();
        props.setServiceUserId(9L);
        tool = new SkillTool(restTemplate, props);
    }

    @Test
    void read_skill_returns_content_when_found() {
        when(restTemplate.getForObject(eq("http://aicoder-skill/api/skill/name/code-review"), eq(Map.class)))
                .thenReturn(Map.of("name", "code-review", "content", "# 审查代码"));

        String result = tool.readSkill("code-review");

        assertTrue(result.contains("审查代码"), result);
    }

    @Test
    void submit_skill_draft_posts_to_skill_service_and_propagates_user_header() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenReturn(Map.of("name", "code-review", "status", "PENDING_APPROVAL"));

        String result = tool.submitSkillDraft("code-review", "审查", "正文");

        assertTrue(result.contains("PENDING_APPROVAL"), result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(any(String.class), captor.capture(), eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals("9", headers.getFirst("X-User-Id"), "应透传 serviceUserId=9");
    }

    @Test
    void submit_skill_draft_failure_returns_friendly_string() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("skill 不可达"));

        String result = tool.submitSkillDraft("code-review", "审查", "正文");
        assertTrue(result.contains("提交失败"), result);
    }
}
