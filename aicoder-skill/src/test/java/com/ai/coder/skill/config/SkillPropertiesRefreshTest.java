package com.ai.coder.skill.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link SkillProperties} 的 {@code @RefreshScope} 在 Nacos 推送（ContextRefresher.refresh()）后
 * 用新的 skill.directory 重建 bean。用窄上下文（排除 datasource/jpa、不加载 nacos import）避免环境依赖。
 */
@SpringBootTest(
        classes = SkillPropertiesRefreshTest.TestApp.class,
        properties = {
                "skill.directory=./before-refresh",
                "spring.config.import=",
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SkillPropertiesRefreshTest {

    @Autowired
    SkillProperties skillProperties;

    @Autowired
    ContextRefresher contextRefresher;

    @Autowired
    ConfigurableEnvironment environment;

    @Test
    void refresh_rebinds_skill_directory_so_resolved_path_changes() {
        String before = skillProperties.getResolvedDirectory();
        assertTrue(before.endsWith("before-refresh"), "初始值来自 skill.directory=./before-refresh");

        // 模拟 Nacos 推送新 skill.directory：注入最高优先级覆盖源后触发 refresh
        environment.getPropertySources().addFirst(
                new MapPropertySource("test-refresh-override", Map.of("skill.directory", "./after-refresh")));
        contextRefresher.refresh();

        String after = skillProperties.getResolvedDirectory();
        assertTrue(after.endsWith("after-refresh"),
                "@RefreshScope 应在 refresh 后用新值重建 bean，resolved=" + after);
        assertNotEquals(before, after, "refresh 前后 resolved 路径应不同");
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EnableConfigurationProperties(SkillProperties.class)
    @EntityScan(basePackages = "unused") // 阻止默认实体扫描触发 JPA
    static class TestApp {
    }
}
