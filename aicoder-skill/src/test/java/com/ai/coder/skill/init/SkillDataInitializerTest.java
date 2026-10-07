package com.ai.coder.skill.init;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.repository.SkillRepository;
import com.ai.coder.skill.service.SkillRegistrySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillDataInitializerTest {

    @TempDir
    Path tempDir;

    private SkillRepository repository;
    private SkillDataInitializer initializer;

    @BeforeEach
    void setUp() {
        repository = mock(SkillRepository.class);
        when(repository.existsByName("greeting-skill")).thenReturn(false);
        when(repository.existsByName("generate-skill")).thenReturn(false);
        when(repository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void run_seeds_both_skills_and_materializes_when_directory_writable() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        initializer = new SkillDataInitializer(repository, new SkillRegistrySyncService(props));

        assertDoesNotThrow(() -> initializer.run());

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository, atLeast(2)).save(captor.capture());
        List<Skill> seeded = captor.getAllValues();
        List<String> names = seeded.stream().map(Skill::getName).toList();
        assertTrue(names.contains("greeting-skill"), "应播种 greeting-skill：" + names);
        assertTrue(names.contains("generate-skill"), "应播种 generate-skill 元技能：" + names);
        // 可写目录 → 两个种子都应物化（filePath 非空）
        assertTrue(seeded.stream().allMatch(s -> s.getFilePath() != null),
                "可写目录下所有种子都应物化");
    }

    @Test
    void run_does_not_crash_when_directory_unwritable() {
        // 把 directory 指向一个普通文件 → createDirectories 必失败 → materialize 抛异常
        // 这是 Fix 1 要优雅暴露的 cwd/权限错配场景：不应崩启动，应降级为「DB 已播种但未物化」
        Path blocker = tempDir.resolve("not-a-dir");
        assertDoesNotThrow(() -> Files.writeString(blocker, "blocker"));
        SkillProperties props = new SkillProperties();
        props.setDirectory(blocker.toString());
        initializer = new SkillDataInitializer(repository, new SkillRegistrySyncService(props));

        assertDoesNotThrow(() -> initializer.run());

        // DB 仍播种（至少一次 save）
        verify(repository, atLeast(1)).save(any(Skill.class));
        // materialize 失败 → 落库的 skill filePath 应为 null（降级，而非崩溃）
        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository, atLeast(1)).save(captor.capture());
        List<Skill> seeded = captor.getAllValues();
        // 不可写目录 → 所有种子的物化都降级（filePath 为 null），但不崩启动
        assertTrue(seeded.stream().allMatch(s -> s.getFilePath() == null),
                "unwritable dir → 所有种子 filePath 都应为 null（graceful degrade）");
    }
}
