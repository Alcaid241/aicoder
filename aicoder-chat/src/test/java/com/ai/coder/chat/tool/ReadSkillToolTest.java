package com.ai.coder.chat.tool;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.ai.coder.chat.config.SkillProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadSkillToolTest {

    @TempDir
    Path tempDir;

    private ReadSkillTool tool;

    @BeforeEach
    void setUp() throws Exception {
        Path skillDir = tempDir.resolve("greeting-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: greeting-skill
                description: 问候
                ---
                # Greeting
                正文
                """);

        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(props.getResolvedDirectory())
                .build();
        tool = new ReadSkillTool(registry);
    }

    @Test
    void readSkill_returns_content_when_found() {
        String content = tool.readSkill("greeting-skill");
        assertTrue(content.contains("# Greeting"), "应返回 SKILL.md 正文");
    }

    @Test
    void readSkill_returns_friendly_hint_when_missing() {
        String content = tool.readSkill("does-not-exist");
        assertFalse(content.contains("# Greeting"));
        assertTrue(content.contains("does-not-exist"), "提示应包含请求的技能名");
    }

    @Test
    void readSkill_rejects_path_traversal_name() {
        assertThrows(IllegalArgumentException.class, () -> tool.readSkill("../etc/evil"));
    }
}
