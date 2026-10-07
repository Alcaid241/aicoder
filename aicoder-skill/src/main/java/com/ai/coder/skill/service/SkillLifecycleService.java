package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 技能生命周期状态机：校验状态迁移合法性。
 * 物化副作用（写/删文件）由 SkillService 在迁移后协调，不放在这里，保持本类纯校验、可单测。
 */
@Service
public class SkillLifecycleService {

    private final Map<SkillStatus, Set<SkillStatus>> transitions = new EnumMap<>(SkillStatus.class);

    public SkillLifecycleService() {
        transitions.put(SkillStatus.DRAFT, EnumSet.of(SkillStatus.PENDING_APPROVAL, SkillStatus.ACTIVE, SkillStatus.REJECTED));
        transitions.put(SkillStatus.PENDING_APPROVAL, EnumSet.of(SkillStatus.ACTIVE, SkillStatus.REJECTED));
        transitions.put(SkillStatus.ACTIVE, EnumSet.of(SkillStatus.ARCHIVED));
        transitions.put(SkillStatus.REJECTED, EnumSet.of(SkillStatus.DRAFT));
        transitions.put(SkillStatus.ARCHIVED, EnumSet.of(SkillStatus.ACTIVE));
    }

    /**
     * 校验从 skill 当前状态迁移到 target 是否合法；非法则抛 IllegalSkillStateException（映射 409）。
     */
    public void assertTransition(Skill skill, SkillStatus target) {
        SkillStatus current = skill.getStatus();
        Set<SkillStatus> allowed = transitions.getOrDefault(current, EnumSet.noneOf(SkillStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalSkillStateException(
                    "非法的状态迁移：%s -> %s（技能 %s）".formatted(current, target, skill.getName()));
        }
    }
}
