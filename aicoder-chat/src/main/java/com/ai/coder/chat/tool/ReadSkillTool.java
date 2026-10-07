package com.ai.coder.chat.tool;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadSkillTool {

    private final SkillRegistry skillRegistry;

    @Tool(description = "读取指定技能的完整 SKILL.md 内容。当系统提示中的技能列表里某个技能适用于当前任务时调用。参数 skillName 为技能名（kebab-case）。")
    public String readSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("技能名不能为空");
        }
        if (skillName.contains("..") || skillName.contains("/") || skillName.contains("\\")
                || skillName.contains(":")) {
            throw new IllegalArgumentException("非法的技能名：" + skillName);
        }
        if (!skillRegistry.contains(skillName)) {
            return "未找到技能：" + skillName + "。可用技能见系统提示中的技能列表，或确认技能名拼写。";
        }
        try {
            return skillRegistry.readSkillContent(skillName);
        } catch (IOException e) {
            log.warn("读取技能 {} 失败", skillName, e);
            return "读取技能 " + skillName + " 失败，请稍后重试。";
        }
    }
}
