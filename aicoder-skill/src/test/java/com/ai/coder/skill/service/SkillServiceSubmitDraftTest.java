package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillServiceSubmitDraftTest {

    @TempDir
    Path tempDir;

    private SkillRepository repository;
    private SkillService service;

    private static final String GOOD_CONTENT = """
            ---
            name: code-review
            description: 对代码变更进行清单式审查并给出修改建议。
            ---
            # Code Review
            当用户提交代码变更时，按以下清单逐项审查：可读性、命名、错误处理、测试覆盖、性能与安全。
            发现问题后给出具体的修改建议与示例代码，避免空泛评价。
            """;

    @BeforeEach
    void setUp() {
        repository = mock(SkillRepository.class);
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        props.setQualityThreshold(60);
        // sync/lifecycle 在 submitDraft 不参与，传真实实例即可（无副作用）
        service = new SkillService(repository,
                new SkillRegistrySyncService(props),
                new SkillLifecycleService(),
                props,
                new SkillQualityScorer());
        when(repository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            if (s.getId() == null) s.setId(1L);
            return s;
        });
    }

    @Test
    void submitDraft_new_well_formed_becomes_pending() {
        when(repository.existsByName("code-review")).thenReturn(false);

        Skill result = service.submitDraft("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT, 99L);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        Skill saved = captor.getValue();
        assertEquals(SkillStatus.PENDING_APPROVAL, saved.getStatus(), "高分应进 PENDING");
        assertEquals(SkillSource.AUTO_GENERATED, saved.getSource());
        assertEquals(1, saved.getVersion());
        assertNull(saved.getParentSkillId());
        assertEquals(99L, saved.getAuthorUserId());
        assertEquals(100, saved.getQualityScore().intValue());
        assertEquals(SkillStatus.PENDING_APPROVAL, result.getStatus());
    }

    @Test
    void submitDraft_malformed_becomes_rejected() {
        when(repository.existsByName("bad name")).thenReturn(false);

        Skill result = service.submitDraft("bad name", "短", "x", null);

        assertEquals(SkillStatus.REJECTED, result.getStatus(), "低分应 REJECTED");
        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        assertEquals(0, captor.getValue().getQualityScore().intValue());
    }

    @Test
    void submitDraft_duplicate_name_versions_as_v2() {
        Skill original = new Skill();
        original.setId(5L);
        original.setName("code-review");
        original.setVersion(1);
        when(repository.existsByName("code-review")).thenReturn(true);
        when(repository.findByName("code-review")).thenReturn(Optional.of(original));
        when(repository.existsByName("code-review-v2")).thenReturn(false);

        service.submitDraft("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT, 99L);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        Skill saved = captor.getValue();
        assertEquals("code-review-v2", saved.getName(), "重名应版本后缀化");
        assertEquals(2, saved.getVersion());
        assertEquals(5L, saved.getParentSkillId());
    }

    @Test
    void submitDraft_duplicate_name_skips_to_v3_when_v2_taken() {
        Skill original = new Skill();
        original.setId(5L);
        original.setName("code-review");
        original.setVersion(1);
        when(repository.existsByName("code-review")).thenReturn(true);
        when(repository.findByName("code-review")).thenReturn(Optional.of(original));
        when(repository.existsByName("code-review-v2")).thenReturn(true);
        when(repository.existsByName("code-review-v3")).thenReturn(false);

        service.submitDraft("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT, 99L);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        Skill saved = captor.getValue();
        assertEquals("code-review-v3", saved.getName(), "v2 占用时应跳到 v3");
        assertEquals(3, saved.getVersion());
        assertEquals(5L, saved.getParentSkillId());
    }
}
