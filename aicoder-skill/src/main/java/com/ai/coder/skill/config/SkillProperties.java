package com.ai.coder.skill.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.io.File;

@Slf4j
@RefreshScope
@Data
@ConfigurationProperties(prefix = "skill")
public class SkillProperties {

    /**
     * 技能物化的共享目录，所有运行面服务的 SkillRegistry 指向同一目录。
     * 可为相对路径（相对 JVM 工作目录）或绝对路径；实际使用前用 {@link #getResolvedDirectory()} 解析为绝对路径。
     */
    private String directory = "./skills";

    /**
     * 自动生成草稿的质量闸门阈值（0–100）。submit_skill_draft 写回的草稿打分 ≥ 此值才进 PENDING_APPROVAL，
     * 否则直接 REJECTED，防低质草稿淹没审批队列。经 Nacos aicoder-shared.yml 可热调（@RefreshScope）。
     */
    private int qualityThreshold = 60;

    /**
     * 把 {@link #directory} 解析为绝对路径。相对路径相对 JVM 工作目录；
     * 已是绝对路径则原样返回（含尾部分隔符，保持幂等）。
     */
    public String getResolvedDirectory() {
        File file = new File(directory);
        return file.isAbsolute() ? directory : file.getAbsolutePath();
    }

    @PostConstruct
    void logResolvedDirectory() {
        // 高亮实际落盘路径，让 cwd 混淆在启动日志即可发现
        log.warn("[skill] directory: configured='{}' resolved='{}'", directory, getResolvedDirectory());
    }
}
