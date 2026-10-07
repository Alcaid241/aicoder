package com.ai.coder.chat.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPropertiesTest {

    @Test
    void resolved_directory_is_absolute_for_relative_input() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("./skills");

        String resolved = props.getResolvedDirectory();

        assertTrue(new File(resolved).isAbsolute(), "解析后应为绝对路径");
        assertEquals("./skills", props.getDirectory(), "原始配置值不变");
    }

    @Test
    void resolved_directory_is_idempotent_for_absolute_input() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("/tmp/some-where");

        assertEquals("/tmp/some-where", props.getResolvedDirectory());
    }
}
