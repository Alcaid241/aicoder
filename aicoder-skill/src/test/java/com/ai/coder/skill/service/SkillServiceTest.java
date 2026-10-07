package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.dto.SkillDTO;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import com.ai.coder.skill.exception.SkillNotFoundException;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillServiceTest {

    @TempDir
    Path tempDir;

    private SkillRepository repository;
    private SkillRegistrySyncService sync;
    private SkillLifecycleService lifecycle;
    private SkillService service;

    @BeforeEach
    void setUp() {
        repository = mock(SkillRepository.class);
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        props.setQualityThreshold(60);
        sync = new SkillRegistrySyncService(props);
        lifecycle = new SkillLifecycleService();
        service = new SkillService(repository, sync, lifecycle, props, new SkillQualityScorer());

        when(repository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            if (s.getId() == null) s.setId(1L);
            return s;
        });
    }

    private SkillDTO draftDto() {
        SkillDTO dto = new SkillDTO();
        dto.setName("pdf-extractor");
        dto.setDisplayName("PDF 抽取");
        dto.setDescription("从 PDF 提取信息");
        dto.setContent("---\nname: pdf-extractor\n---\n正文");
        return dto;
    }

    private Skill persisted(SkillStatus status) {
        Skill s = new Skill();
        s.setId(1L);
        s.setName("pdf-extractor");
        s.setDescription("从 PDF 提取信息");
        s.setContent("---\nname: pdf-extractor\n---\n正文");
        s.setStatus(status);
        s.setSource(SkillSource.MANUAL);
        s.setVersion(1);
        return s;
    }

    @Test
    void create_persists_draft_with_manual_source() {
        when(repository.existsByName("pdf-extractor")).thenReturn(false);

        Skill created = service.create(draftDto(), 99L);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        Skill saved = captor.getValue();
        assertEquals(SkillStatus.DRAFT, saved.getStatus());
        assertEquals(SkillSource.MANUAL, saved.getSource());
        assertEquals(1, saved.getVersion());
        assertEquals(99L, saved.getAuthorUserId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void create_rejects_duplicate_name() {
        when(repository.existsByName("pdf-extractor")).thenReturn(true);
        assertThrows(DuplicateSkillException.class, () -> service.create(draftDto(), 99L));
    }

    @Test
    void getById_not_found_throws_SkillNotFoundException() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(SkillNotFoundException.class, () -> service.getById(404L));
    }

    @Test
    void getByName_returns_skill_when_found() {
        Skill skill = new Skill();
        skill.setName("code-review");
        when(repository.findByName("code-review")).thenReturn(Optional.of(skill));

        assertSame(skill, service.getByName("code-review"));
    }

    @Test
    void getByName_throws_when_missing() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());
        assertThrows(SkillNotFoundException.class, () -> service.getByName("nope"));
    }

    @Test
    void listAll_returns_all() {
        when(repository.findAll()).thenReturn(List.of(persisted(SkillStatus.DRAFT)));
        assertEquals(1, service.listAll().size());
    }

    @Test
    void listPending_returns_pending_only() {
        when(repository.findByStatusOrderByNameAsc(SkillStatus.PENDING_APPROVAL))
                .thenReturn(List.of(persisted(SkillStatus.PENDING_APPROVAL)));
        assertEquals(1, service.listPending().size());
    }

    @Test
    void submit_transitions_draft_to_pending() {
        Skill s = persisted(SkillStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        Skill result = service.submitForApproval(1L);

        assertEquals(SkillStatus.PENDING_APPROVAL, result.getStatus());
    }

    @Test
    void submit_rejects_illegal_transition_from_active() {
        Skill s = persisted(SkillStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThrows(IllegalSkillStateException.class, () -> service.submitForApproval(1L));
        verify(repository, never()).save(any(Skill.class));
    }

    @Test
    void approve_transitions_pending_to_active_and_materializes() {
        Skill s = persisted(SkillStatus.PENDING_APPROVAL);
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        Skill result = service.approve(1L, 7L);

        assertEquals(SkillStatus.ACTIVE, result.getStatus());
        assertEquals(7L, result.getApprovedBy());
        assertNotNull(result.getApprovedAt());
        assertNotNull(result.getFilePath());
        assertEquals("pdf-extractor/SKILL.md", result.getFilePath());
        verify(repository, times(1)).save(any(Skill.class));
    }

    @Test
    void approve_rejects_illegal_transition_from_rejected() {
        // REJECTED 只允许 -> DRAFT，不可直接 -> ACTIVE；被拒技能须先回到 DRAFT 重新提审
        Skill s = persisted(SkillStatus.REJECTED);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThrows(IllegalSkillStateException.class, () -> service.approve(1L, 7L));
        verify(repository, never()).save(any(Skill.class));
    }

    @Test
    void approve_republishes_archived_skill_to_active() {
        // ARCHIVED -> ACTIVE 在状态机中合法：归档技能可经审批直接重新发布（可逆）
        Skill s = persisted(SkillStatus.ARCHIVED);
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        Skill result = service.approve(1L, 7L);

        assertEquals(SkillStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getFilePath());
    }

    @Test
    void reject_transitions_pending_to_rejected_without_materialize() {
        Skill s = persisted(SkillStatus.PENDING_APPROVAL);
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        Skill result = service.reject(1L);

        assertEquals(SkillStatus.REJECTED, result.getStatus());
        verify(repository, times(1)).save(any(Skill.class));
    }

    @Test
    void archive_active_removes_file_and_sets_archived() throws Exception {
        Skill s = persisted(SkillStatus.ACTIVE);
        s.setFilePath("pdf-extractor/SKILL.md");
        sync.materialize(s); // 先物化，模拟此前审批通过的状态
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        Skill result = service.archive(1L);

        assertEquals(SkillStatus.ARCHIVED, result.getStatus());
        assertFalse(Files.exists(tempDir.resolve("pdf-extractor/SKILL.md")));
    }

    @Test
    void delete_active_skill_removes_file_then_deletes_row() throws Exception {
        Skill s = persisted(SkillStatus.ACTIVE);
        s.setFilePath("pdf-extractor/SKILL.md");
        sync.materialize(s); // 先物化，模拟 ACTIVE 技能
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        service.delete(1L);

        assertFalse(Files.exists(tempDir.resolve("pdf-extractor/SKILL.md")));
        verify(repository).deleteById(1L);
    }
}
