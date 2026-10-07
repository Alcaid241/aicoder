package com.ai.coder.chat.config;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void builds_registry_and_advisor_pointing_at_skill_directory() throws Exception {
        Path skillDir = tempDir.resolve("faq-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: faq-skill
                description: 常见问题
                ---
                # FAQ
                """);

        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        SkillRuntimeConfig config = new SkillRuntimeConfig(props);

        SkillRegistry registry = config.skillRegistry();
        assertNotNull(registry);
        assertEquals(1, registry.size(), "应扫描到 1 个技能");
        assertTrue(registry.contains("faq-skill"));

        SkillPromptAugmentAdvisor advisor = config.skillPromptAugmentAdvisor(registry);
        assertNotNull(advisor);
        assertEquals(1, advisor.getSkillCount());
    }

    /**
     * FileSystemSkillRegistry 默认会扫描用户级 ${user.home}/saa/skills（builder 把空串/null 当默认）。
     * 该目录下的技能不经审批，会泄漏进 chat 的技能目录注入。必须用哨兵路径显式禁用：
     * 哨兵路径不存在，loadSkillsToRegistry 的 Files.exists 门控会跳过扫描（含 reload 重扫）。
     */
    @Test
    void user_skills_directory_is_disabled_so_unapproved_skills_cannot_leak() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        SkillRuntimeConfig config = new SkillRuntimeConfig(props);

        SkillRegistry registry = config.skillRegistry();
        FileSystemSkillRegistry fsRegistry = assertInstanceOf(FileSystemSkillRegistry.class, registry);
        String userDir = fsRegistry.getUserSkillsDirectory();

        String defaultUserDir = System.getProperty("user.home") + "/saa/skills";
        assertFalse(userDir.equals(defaultUserDir),
                "用户级技能目录必须被禁用（默认 " + defaultUserDir + "），否则未审批技能会泄漏进 chat 目录");
        assertTrue(Files.notExists(Path.of(userDir)),
                "哨兵路径不应存在，确保扫描被 Files.exists 门控跳过：" + userDir);
        assertFalse(userDir.isBlank(), "哨兵不能是空串（builder 把空串当默认 ~/saa/skills）");
    }
}
