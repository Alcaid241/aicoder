package com.ai.coder.chat.config;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 构建技能运行面单例：FileSystemSkillRegistry（扫描共享 skills/ 目录）+ SkillPromptAugmentAdvisor（注入目录到系统提示）。
 * 目录缺失时创建空目录使 registry 优雅降级为空（chat 仍可用，仅无技能目录注入）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SkillRuntimeConfig {

    /**
     * 哨兵路径：显式禁用用户级 {@code ${user.home}/saa/skills} 扫描。
     * <p>{@link FileSystemSkillRegistry.Builder} 把 {@code null}/空串的 userSkillsDirectory 当作"用默认
     * {@code ~/saa/skills}"，因此必须设一个非空、且永不存在的路径。{@code loadSkillsToRegistry()}（含每次
     * reload 重扫）对用户级目录有 {@code Files.exists} 门控，哨兵路径不存在即整段跳过，全生命周期禁用。
     * <p>为什么禁用：chat 只应消费经 aicoder-skill 审批并物化到共享目录的 ACTIVE 技能；用户级目录未经审批，
     * 放任扫描会让任意本地技能泄漏进系统提示。
     */
    private static final String DISABLED_USER_SKILLS_DIRECTORY =
            "/__aicoder_user_skills_disabled__";

    private final SkillProperties skillProperties;

    @Bean
    public SkillRegistry skillRegistry() {
        String dir = skillProperties.getResolvedDirectory();
        try {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.warn("[skill] 共享技能目录不存在，已创建空目录：{}", dir);
            }
        } catch (Exception e) {
            log.warn("[skill] 创建技能目录失败 {}：{}", dir, e.getMessage());
        }
        return FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(dir)
                .userSkillsDirectory(DISABLED_USER_SKILLS_DIRECTORY)
                .build();
    }

    @Bean
    public SkillPromptAugmentAdvisor skillPromptAugmentAdvisor(SkillRegistry registry) {
        return SkillPromptAugmentAdvisor.builder()
                .skillRegistry(registry)
                .build();
    }
}
