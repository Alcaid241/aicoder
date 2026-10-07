package com.ai.coder.skill.init;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import com.ai.coder.skill.service.SkillRegistrySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class SkillDataInitializer implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final SkillRegistrySyncService syncService;

    @Override
    public void run(String... args) {
        seedIfAbsent("greeting-skill", "问候技能",
                "当用户打招呼或寒暄时使用，以友好的方式回应。",
                """
                ---
                name: greeting-skill
                description: 当用户打招呼或寒暄时使用，以友好的方式回应。
                ---
                # 问候技能
                遇到问候时，先简短回应，再询问可以帮什么忙。保持语气友好、简洁。
                """);
        seedIfAbsent("generate-skill", "生成技能（元技能）",
                "当技能目录中没有任何技能能匹配用户任务、且该任务是可复用的重复性模式时使用——起草一个新的标准化技能并提交审批。",
                """
                ---
                name: generate-skill
                description: 当技能目录中没有任何技能能匹配用户任务、且该任务是可复用的重复性模式时使用——起草一个新的标准化技能并提交审批。
                ---
                # Generate Skill（元技能）

                ## 何时使用
                - 技能目录中无匹配技能，**且**判断该任务以后会重复出现。
                - 一次性问题不要生成技能。

                ## 如何起草
                1. 起一个 kebab-case 的技能名（短、表意，如 `code-review-checklist`）。
                2. 写一句话 description（何时使用）。
                3. 按 SKILL.md 模板写正文：frontmatter(name + description) + 指令步骤 + 必要示例。

                ## 质量标准（低质草稿会被自动拒绝）
                - 名字规范（kebab-case）、描述清晰（10–200 字）、含合法 frontmatter、正文 ≥50 字、指令可执行。

                ## 完成动作
                调用工具：`submit_skill_draft(name, description, content)`
                其中 content 为完整 SKILL.md 文本（含 frontmatter）。
                """);
    }

    private void seedIfAbsent(String name, String displayName, String description, String content) {
        if (skillRepository.existsByName(name)) {
            return;
        }
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDisplayName(displayName);
        skill.setDescription(description);
        skill.setContent(content);
        skill.setVersion(1);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setSource(SkillSource.MANUAL);
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        Skill saved = skillRepository.save(skill);
        try {
            saved.setFilePath(syncService.materialize(saved));
            skillRepository.save(saved);
            log.info("已播种示例技能 {} 并物化到 {}", saved.getName(), saved.getFilePath());
        } catch (Exception e) {
            // 目录不可写/IO 失败时降级：技能已入库但未物化（filePath 留空），不阻断启动。
            log.warn("播种 {}：已写入 DB 但物化失败（检查 skill.directory 权限/路径）：{}", saved.getName(), e.getMessage());
        }
    }
}
