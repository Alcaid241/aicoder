package com.ai.coder.chat.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * submit_skill_draft 工具：模型判定无匹配技能、且任务可复用时，起草 SKILL.md 写回 aicoder-skill 审批队列。
 * 用 @LoadBalanced RestTemplate（与 SqlQueryTool 同模式）→ POST http://aicoder-skill/api/skill/draft。
 * userId 经 ToolContext 透传（模型不感知），写入 X-User-Id 头。任何失败降级为友好串，不中断对话轮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitSkillDraftTool {

    private static final String DRAFT_URL = "http://aicoder-skill/api/skill/draft";

    private final RestTemplate restTemplate;

    @Tool(description = "提交一个新技能草稿进入审批队列。当判断当前任务可复用、但技能目录里没有匹配技能时使用。"
            + "参数：name（kebab-case 技能名）、description（一句话描述何时使用）、"
            + "content（完整 SKILL.md 文本，含 frontmatter）。")
    public String submitSkillDraft(String name, String description, String content, ToolContext toolContext) {
        if (name == null || name.isBlank()) {
            return "技能名不能为空，未提交。";
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return "非法的技能名：" + name + "，未提交。";
        }
        Long userId = extractUserId(toolContext);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (userId != null) {
                headers.set("X-User-Id", String.valueOf(userId));
            }
            Map<String, Object> body = Map.of(
                    "name", name,
                    "description", description == null ? "" : description,
                    "content", content == null ? "" : content);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(DRAFT_URL, entity, Map.class);
            String respName = resp == null ? name : String.valueOf(resp.getOrDefault("name", name));
            String status = resp == null ? "未知" : String.valueOf(resp.getOrDefault("status", "未知"));
            log.info("submit_skill_draft 提交：name={} → status={}", respName, status);
            return "技能草稿「" + respName + "」已提交，状态：" + status + "。";
        } catch (Exception e) {
            log.warn("submit_skill_draft 提交失败 name={}", name, e);
            return "技能提交失败：" + e.getMessage() + "。请稍后重试。";
        }
    }

    private Long extractUserId(ToolContext toolContext) {
        if (toolContext == null) return null;
        Object v = toolContext.getContext().get("userId");
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
