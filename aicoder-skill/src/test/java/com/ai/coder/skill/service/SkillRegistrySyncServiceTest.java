package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistrySyncServiceTest {

    @TempDir
    Path tempDir;

    private SkillRegistrySyncService service;

    @BeforeEach
    void setUp() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        service = new SkillRegistrySyncService(props);
    }

    private Skill skill(String name, String content) {
        Skill s = new Skill();
        s.setName(name);
        s.setContent(content);
        return s;
    }

    @Test
    void materialize_writes_skill_md_with_content() throws Exception {
        Skill s = skill("pdf-extractor", "---\nname: pdf-extractor\n---\n正文");

        String relPath = service.materialize(s);

        Path file = tempDir.resolve(relPath);
        assertEquals("pdf-extractor/SKILL.md", relPath);
        assertTrue(Files.exists(file));
        assertEquals("---\nname: pdf-extractor\n---\n正文", Files.readString(file));
    }

    @Test
    void materialize_overwrites_existing_file() throws Exception {
        service.materialize(skill("pdf-extractor", "v1"));
        service.materialize(skill("pdf-extractor", "v2"));

        Path file = tempDir.resolve("pdf-extractor/SKILL.md");
        assertEquals("v2", Files.readString(file));
    }

    @Test
    void remove_deletes_skill_directory() throws Exception {
        service.materialize(skill("pdf-extractor", "内容"));
        assertTrue(Files.exists(tempDir.resolve("pdf-extractor/SKILL.md")));

        service.remove("pdf-extractor");

        assertFalse(Files.exists(tempDir.resolve("pdf-extractor")));
    }

    @Test
    void remove_is_idempotent_when_missing() {
        // 目录不存在时不应抛异常
        service.remove("does-not-exist");
    }

    @Test
    void materialize_rejects_path_traversal_name() {
        Skill s = skill("../etc/evil", "x");
        assertThrows(IllegalArgumentException.class, () -> service.materialize(s));
    }

    @Test
    void remove_rejects_path_traversal_name() {
        assertThrows(IllegalArgumentException.class, () -> service.remove("../etc/evil"));
    }
}
