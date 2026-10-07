package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.config.McpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 技能读写工具：经 @LoadBalanced RestTemplate 调 aicoder-skill。
 * - read_skill：GET /api/skill/name/{name}
 * - submit_skill_draft：POST /api/skill/draft（请求体 SkillDraftRequest，X-User-Id 透传服务账号）
 */
@Slf4j
@Component
public class SkillTool {

    private static final String BASE_URL = "http://aicoder-skill/api/skill";

    private final RestTemplate restTemplate;
    private final McpProperties props;

    public SkillTool(@Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate, McpProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    @Tool(description = "读取平台技能库中指定技能的完整内容。参数 name 为技能名（kebab-case）。")
    public String readSkill(String name) {
        if (name == null || name.isBlank()) {
            return "技能名不能为空。";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> skill = restTemplate.getForObject(
                    BASE_URL + "/name/" + name, Map.class);
            if (skill == null) {
                return "未找到技能：" + name;
            }
            String content = String.valueOf(skill.getOrDefault("content", ""));
            return "技能「" + skill.getOrDefault("name", name) + "」：\n" + content;
        } catch (Exception e) {
            log.warn("read_skill 失败 name={}", name, e);
            return "读取技能失败：" + e.getMessage();
        }
    }

    @Tool(description = "提交一个新技能草稿进入平台审批队列。参数：name（kebab-case）、description（一句话描述）、content（完整技能文本）。")
    public String submitSkillDraft(String name, String description, String content) {
        if (name == null || name.isBlank()) {
            return "技能名不能为空，未提交。";
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return "非法的技能名：" + name + "，未提交。";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", String.valueOf(props.getServiceUserId()));
            Map<String, Object> body = Map.of(
                    "name", name,
                    "description", description == null ? "" : description,
                    "content", content == null ? "" : content);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(BASE_URL + "/draft", entity, Map.class);
            String status = resp == null ? "未知" : String.valueOf(resp.getOrDefault("status", "未知"));
            return "技能草稿「" + name + "」已提交，状态：" + status + "。";
        } catch (Exception e) {
            log.warn("submit_skill_draft 失败 name={}", name, e);
            return "技能提交失败：" + e.getMessage() + "。请稍后重试。";
        }
    }
}
