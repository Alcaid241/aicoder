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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillRegistrySyncService syncService;
    private final SkillLifecycleService lifecycleService;
    private final SkillProperties skillProperties;
    private final SkillQualityScorer qualityScorer;

    @Transactional
    public Skill create(SkillDTO dto, Long authorUserId) {
        if (skillRepository.existsByName(dto.getName())) {
            throw new DuplicateSkillException("技能名已存在：" + dto.getName());
        }
        Skill skill = new Skill();
        skill.setName(dto.getName());
        skill.setDisplayName(dto.getDisplayName());
        skill.setDescription(dto.getDescription());
        skill.setContent(dto.getContent());
        skill.setVersion(1);
        skill.setStatus(SkillStatus.DRAFT);
        skill.setSource(dto.getSource() != null ? dto.getSource() : SkillSource.MANUAL);
        skill.setCategory(dto.getCategory());
        skill.setTags(dto.getTags());
        skill.setAuthorUserId(authorUserId);
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    /**
     * 自动生成草稿写回：重名版本后缀化（V1）→ 建 AUTO_GENERATED 草稿 → 启发式打分 →
     * 分数 ≥ 阈值进 PENDING_APPROVAL，否则 REJECTED。单事务。
     */
    @Transactional
    public Skill submitDraft(String name, String description, String content, Long authorUserId) {
        String finalName = name;
        int version = 1;
        Long parentSkillId = null;
        if (skillRepository.existsByName(name)) {
            Skill original = skillRepository.findByName(name)
                    .orElseThrow(() -> new IllegalSkillStateException("技能存在但读取失败：" + name));
            parentSkillId = original.getId();
            version = nextFreeVersion(name, original.getVersion());
            finalName = name + "-v" + version;
        }

        Skill skill = new Skill();
        skill.setName(finalName);
        skill.setDescription(description);
        skill.setContent(content);
        skill.setVersion(version);
        skill.setSource(SkillSource.AUTO_GENERATED);
        skill.setParentSkillId(parentSkillId);
        skill.setAuthorUserId(authorUserId);
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());

        ScoreResult scoreResult = qualityScorer.score(finalName, description, content);
        skill.setQualityScore(BigDecimal.valueOf(scoreResult.score()));
        skill.setStatus(scoreResult.score() >= skillProperties.getQualityThreshold()
                ? SkillStatus.PENDING_APPROVAL
                : SkillStatus.REJECTED);
        return skillRepository.save(skill);
    }

    /** 选取最小 N≥max(2, hint+1) 使 base+"-v"+N 不存在（自增扫描既有名）。 */
    private int nextFreeVersion(String base, int hint) {
        int n = Math.max(2, hint + 1);
        while (skillRepository.existsByName(base + "-v" + n)) {
            n++;
        }
        return n;
    }

    public List<Skill> listAll() {
        return skillRepository.findAll();
    }

    public List<Skill> listPending() {
        return skillRepository.findByStatusOrderByNameAsc(SkillStatus.PENDING_APPROVAL);
    }

    public Skill getById(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("技能不存在：" + id));
    }

    public Skill getByName(String name) {
        if (name == null || name.isBlank()) {
            throw new SkillNotFoundException("技能名不能为空");
        }
        return skillRepository.findByName(name)
                .orElseThrow(() -> new SkillNotFoundException("技能不存在：" + name));
    }

    @Transactional
    public Skill update(Long id, SkillDTO dto) {
        Skill skill = getById(id);
        skill.setDisplayName(dto.getDisplayName());
        skill.setDescription(dto.getDescription());
        skill.setContent(dto.getContent());
        skill.setCategory(dto.getCategory());
        skill.setTags(dto.getTags());
        skill.setUpdatedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    @Transactional
    public void delete(Long id) {
        Skill skill = getById(id);
        if (skill.getStatus() == SkillStatus.ACTIVE) {
            syncService.remove(skill.getName());
        }
        skillRepository.deleteById(id);
    }

    @Transactional
    public Skill submitForApproval(Long id) {
        Skill skill = getById(id);
        lifecycleService.assertTransition(skill, SkillStatus.PENDING_APPROVAL);
        skill.setStatus(SkillStatus.PENDING_APPROVAL);
        skill.setUpdatedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    @Transactional
    public Skill approve(Long id, Long approverId) {
        Skill skill = getById(id);
        lifecycleService.assertTransition(skill, SkillStatus.ACTIVE);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setApprovedBy(approverId);
        skill.setApprovedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        skill.setFilePath(syncService.materialize(skill));
        return skillRepository.save(skill);
    }

    @Transactional
    public Skill reject(Long id) {
        Skill skill = getById(id);
        lifecycleService.assertTransition(skill, SkillStatus.REJECTED);
        skill.setStatus(SkillStatus.REJECTED);
        skill.setUpdatedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    @Transactional
    public Skill archive(Long id) {
        Skill skill = getById(id);
        lifecycleService.assertTransition(skill, SkillStatus.ARCHIVED);
        skill.setStatus(SkillStatus.ARCHIVED);
        skill.setUpdatedAt(LocalDateTime.now());
        // 注意：文件 I/O 非事务性，无法与 DB 提交原子化。Phase 1 接受这一权衡——
        // 不一致时由运行面注册表周期 rescan 兜底（reload 通知在 Phase 2 接入）。
        syncService.remove(skill.getName());
        skill.setFilePath(null);
        return skillRepository.save(skill);
    }
}
