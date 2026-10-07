package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * 把 ACTIVE 技能物化为 SKILL.md 到共享目录，或删除已下线技能的目录。
 * 写入路径：{directory}/{name}/SKILL.md。返回相对路径 "{name}/SKILL.md"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillRegistrySyncService {

    private static final String SKILL_FILE = "SKILL.md";

    private final SkillProperties skillProperties;

    /**
     * 写（或覆盖）技能文件，返回相对路径。
     */
    public String materialize(Skill skill) {
        try {
            Path skillDir = resolveSkillDir(skill.getName());
            Files.createDirectories(skillDir);
            Path file = skillDir.resolve(SKILL_FILE);
            Files.writeString(file, skill.getContent());
            log.info("已物化技能 {} -> {}", skill.getName(), file);
            return skill.getName() + "/" + SKILL_FILE;
        } catch (IOException e) {
            throw new IllegalStateException("物化技能失败：" + skill.getName(), e);
        }
    }

    /**
     * 删除技能目录（幂等）。
     */
    public void remove(String skillName) {
        Path skillDir = resolveSkillDir(skillName);
        if (!Files.exists(skillDir)) {
            return;
        }
        try (var paths = Files.walk(skillDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除 {} 失败", p, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("删除技能目录失败：" + skillName, e);
        }
    }

    private Path resolveSkillDir(String skillName) {
        Path base = Paths.get(skillProperties.getResolvedDirectory()).normalize();
        Path resolved = base.resolve(skillName).normalize();
        // 防止路径穿越：技能名不得跳出共享根目录（如 ../etc）
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("非法技能名（禁止路径穿越）：" + skillName);
        }
        return resolved;
    }
}
