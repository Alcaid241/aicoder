package com.ai.coder.skill.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPropertiesTest {

    @Test
    void resolved_directory_is_absolute() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("./skills");

        String resolved = props.getResolvedDirectory();

        assertTrue(new File(resolved).isAbsolute(), "解析后应为绝对路径");
        // 原始配置值保持不变（相对路径）
        assertEquals("./skills", props.getDirectory());
    }

    @Test
    void resolved_directory_reflects_set_value() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("/tmp/some-where");

        assertEquals("/tmp/some-where", props.getResolvedDirectory());
    }

    @Test
    void env_override_value_is_kept() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(System.getProperty("java.io.tmpdir"));

        String resolved = props.getResolvedDirectory();
        assertEquals(System.getProperty("java.io.tmpdir"), resolved);
        // 与默认值不同，证明注入生效
        assertNotEquals("./skills", resolved);
    }
}
