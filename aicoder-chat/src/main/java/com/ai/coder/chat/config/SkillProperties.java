package com.ai.coder.chat.config;

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
     * 技能共享目录（与 aicoder-skill 指向同一物理路径，经 Nacos aicoder-shared.yml 统一）。
     * 可为相对（相对 JVM 工作目录）或绝对；用 {@link #getResolvedDirectory()} 取绝对路径。
     */
    private String directory = "./skills";

    /** 把 directory 解析为绝对路径；相对路径相对 JVM 工作目录。绝对输入幂等。 */
    public String getResolvedDirectory() {
        File file = new File(directory);
        return file.isAbsolute() ? directory : file.getAbsolutePath();
    }

    @PostConstruct
    void logResolvedDirectory() {
        log.warn("[skill] directory: configured='{}' resolved='{}'", directory, getResolvedDirectory());
    }
}
