package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillLifecycleServiceTest {

    private final SkillLifecycleService service = new SkillLifecycleService();

    private Skill skill(SkillStatus status) {
        Skill s = new Skill();
        s.setId(1L);
        s.setName("x");
        s.setStatus(status);
        return s;
    }

    @Test
    void draft_to_pending_is_allowed() {
        assertDoesNotThrow(() -> service.assertTransition(skill(SkillStatus.DRAFT), SkillStatus.PENDING_APPROVAL));
    }

    @Test
    void draft_to_active_is_allowed() {
        assertDoesNotThrow(() -> service.assertTransition(skill(SkillStatus.DRAFT), SkillStatus.ACTIVE));
    }

    @Test
    void pending_to_active_is_allowed() {
        assertDoesNotThrow(() -> service.assertTransition(skill(SkillStatus.PENDING_APPROVAL), SkillStatus.ACTIVE));
    }

    @Test
    void active_to_archived_is_allowed() {
        assertDoesNotThrow(() -> service.assertTransition(skill(SkillStatus.ACTIVE), SkillStatus.ARCHIVED));
    }

    @Test
    void draft_to_archived_is_forbidden() {
        assertThrows(IllegalSkillStateException.class,
                () -> service.assertTransition(skill(SkillStatus.DRAFT), SkillStatus.ARCHIVED));
    }

    @Test
    void active_to_draft_is_forbidden() {
        assertThrows(IllegalSkillStateException.class,
                () -> service.assertTransition(skill(SkillStatus.ACTIVE), SkillStatus.DRAFT));
    }

    @Test
    void rejected_to_draft_is_allowed() {
        assertDoesNotThrow(() -> service.assertTransition(skill(SkillStatus.REJECTED), SkillStatus.DRAFT));
    }

    @Test
    void archived_to_active_is_allowed() {
        assertDoesNotThrow(() -> service.assertTransition(skill(SkillStatus.ARCHIVED), SkillStatus.ACTIVE));
    }
}
