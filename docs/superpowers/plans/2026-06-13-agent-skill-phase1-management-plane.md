# Agent Skill 体系 — Phase 1：管理面 aicoder-skill 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `aicoder-skill` (:8086) 管理面微服务，实现技能的 CRUD、生命周期状态机、审批流，并把审批激活的技能物化为 SKILL.md 写入共享技能目录。

**Architecture:** 纯 CRUD + 文件物化服务，与 aicoder-admin 同构（Spring Boot + JPA + Nacos）。DB（`ai_skill` 表）为唯一真相源；ACTIVE 技能被物化到配置的共享目录 `{skill.directory}/{name}/SKILL.md`，供 Phase 2 运行面（chat）的原生 SkillRegistry 扫描。本阶段**不依赖** graph-core，不改 chat。

**Tech Stack:** Spring Boot 3.5.13, Spring Data JPA, MySQL, Lombok, JUnit 5 + Mockito, Nacos 服务发现, Spring Cloud Gateway。

**关联设计：** [docs/superpowers/specs/2026-06-13-agent-skill-system-design.md](../specs/2026-06-13-agent-skill-system-design.md)

**范围边界（本计划不做）：** chat 运行面接入（SkillPromptAugmentAdvisor/ReadSkillTool）、元技能生成闭环（SkillGenerationService/QualityScorer）、前端管理页。这些是后续独立计划。

---

## File Structure

**Create:**
- `aicoder-skill/pom.xml` — 模块依赖（web/data-jpa/data-redis/validation/mysql/jedis/nacos/springdoc/lombok/test）
- `aicoder-skill/src/main/resources/application.yml` — 端口 8086、test_ai 数据源、`skill.directory` 配置
- `aicoder-skill/src/main/java/com/ai/coder/skill/AicoderSkillApplication.java` — 启动类
- `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java` — `@ConfigurationProperties("skill")`，含 `directory`
- `aicoder-skill/src/main/java/com/ai/coder/skill/entity/Skill.java` — JPA 实体，镜像 `ai_skill`
- `aicoder-skill/src/main/java/com/ai/coder/skill/entity/SkillStatus.java` — 枚举：DRAFT/PENDING_APPROVAL/ACTIVE/REJECTED/ARCHIVED
- `aicoder-skill/src/main/java/com/ai/coder/skill/entity/SkillSource.java` — 枚举：MANUAL/AUTO_GENERATED
- `aicoder-skill/src/main/java/com/ai/coder/skill/repository/SkillRepository.java` — JpaRepository + 查询方法
- `aicoder-skill/src/main/java/com/ai/coder/skill/dto/SkillDTO.java` — 请求/响应 DTO
- `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillLifecycleService.java` — 状态机迁移校验
- `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillRegistrySyncService.java` — 物化/删除 SKILL.md
- `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java` — CRUD + 审批编排
- `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java` — REST `/api/skill/**`
- `aicoder-skill/src/main/java/com/ai/coder/skill/init/SkillDataInitializer.java` — 启动播种一个示例 ACTIVE 技能
- `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillLifecycleServiceTest.java`
- `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillRegistrySyncServiceTest.java`
- `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`

**Modify:**
- `pom.xml` — `<modules>` 增加 `aicoder-skill`
- `aicoder-gateway/src/main/resources/application.yml` — 增加 `skill-service` 路由
- `sql/schema.sql` — 增加 `ai_skill` 表 DDL

---

### Task 1: 模块脚手架（pom + 启动类 + 配置）

**Files:**
- Create: `aicoder-skill/pom.xml`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/AicoderSkillApplication.java`
- Create: `aicoder-skill/src/main/resources/application.yml`
- Modify: `pom.xml`（根，加 `<module>aicoder-skill</module>`）

- [ ] **Step 1: 创建 `aicoder-skill/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.coder</groupId>
        <artifactId>aicoder</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>aicoder-skill</artifactId>
    <name>AI Coder Skill</name>
    <description>Agent Skill 管理面：技能注册、生命周期、审批、物化</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>redis.clients</groupId>
            <artifactId>jedis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建启动类 `AicoderSkillApplication.java`**

```java
package com.ai.coder.skill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class AicoderSkillApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderSkillApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 `application.yml`**

```yaml
server:
  port: 8086

spring:
  application:
    name: aicoder-skill
  datasource:
    url: jdbc:mysql://localhost:3306/test_ai?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}

# 技能物化共享目录：所有运行面服务（chat/rag/workflow）的 SkillRegistry 指向同一目录。
# 本地开发用相对路径 ./skills；生产用共享挂载卷绝对路径。
skill:
  directory: ./skills

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 4: 根 `pom.xml` `<modules>` 增加 aicoder-skill**

在 `<module>aicoder-system</module>` 之后新增一行：

```xml
        <module>aicoder-skill</module>
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl aicoder-skill -am -DskipTests`
Expected: BUILD SUCCESS（模块可被识别并编译）。

- [ ] **Step 6: Commit**

```bash
git add aicoder-skill/pom.xml aicoder-skill/src/main/java/com/ai/coder/skill/AicoderSkillApplication.java aicoder-skill/src/main/resources/application.yml pom.xml
git commit -m "feat(skill): 新增 aicoder-skill 模块脚手架 (:8086)"
```

---

### Task 2: DDL + Skill 实体 + 枚举

**Files:**
- Modify: `sql/schema.sql`（追加 `ai_skill` 表）
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/entity/SkillStatus.java`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/entity/SkillSource.java`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/entity/Skill.java`

- [ ] **Step 1: `sql/schema.sql` 末尾追加 `ai_skill` 表**

```sql

-- 16. Agent 技能表（管理面唯一真相源）
CREATE TABLE IF NOT EXISTS ai_skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '技能目录名 kebab-case',
    display_name VARCHAR(128) COMMENT '展示名',
    description VARCHAR(512) NOT NULL COMMENT '一句话描述，注入技能目录',
    content LONGTEXT NOT NULL COMMENT '完整 SKILL.md 正文（含 front-matter）',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_APPROVAL/ACTIVE/REJECTED/ARCHIVED',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO_GENERATED',
    category VARCHAR(64) COMMENT '分组',
    tags VARCHAR(256) COMMENT '标签',
    quality_score DECIMAL(5,2) COMMENT '质量分 0-100',
    trial_result TEXT COMMENT '试运行结果 JSON',
    file_path VARCHAR(512) COMMENT '物化路径，ACTIVE 前为 NULL',
    parent_skill_id BIGINT COMMENT '版本血缘',
    author_user_id BIGINT COMMENT '作者',
    approved_by BIGINT COMMENT '审批人',
    approved_at DATETIME COMMENT '审批时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_name (name),
    INDEX idx_skill_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 技能表';
```

- [ ] **Step 2: 创建枚举 `SkillStatus.java`**

```java
package com.ai.coder.skill.entity;

public enum SkillStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    ARCHIVED
}
```

- [ ] **Step 3: 创建枚举 `SkillSource.java`**

```java
package com.ai.coder.skill.entity;

public enum SkillSource {
    MANUAL,
    AUTO_GENERATED
}
```

- [ ] **Step 4: 创建实体 `Skill.java`**

```java
package com.ai.coder.skill.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_skill")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 128)
    private String displayName;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillSource source;

    @Column(length = 64)
    private String category;

    @Column(length = 256)
    private String tags;

    @Column(precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(columnDefinition = "TEXT")
    private String trialResult;

    @Column(length = 512)
    private String filePath;

    private Long parentSkillId;

    private Long authorUserId;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl aicoder-skill -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add sql/schema.sql aicoder-skill/src/main/java/com/ai/coder/skill/entity/
git commit -m "feat(skill): ai_skill 表 DDL + Skill 实体与状态/来源枚举"
```

---

### Task 3: SkillProperties + Repository

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/repository/SkillRepository.java`

- [ ] **Step 1: 创建 `SkillProperties.java`**

```java
package com.ai.coder.skill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "skill")
public class SkillProperties {

    /** 技能物化的共享目录，所有运行面服务的 SkillRegistry 指向同一目录。 */
    private String directory = "./skills";
}
```

- [ ] **Step 2: 创建 `SkillRepository.java`**

```java
package com.ai.coder.skill.repository;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByName(String name);

    boolean existsByName(String name);

    List<Skill> findByStatus(SkillStatus status);

    List<Skill> findByStatusOrderByNameAsc(SkillStatus status);
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl aicoder-skill -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java aicoder-skill/src/main/java/com/ai/coder/skill/repository/SkillRepository.java
git commit -m "feat(skill): SkillProperties 配置 + SkillRepository"
```

---

### Task 4: 生命周期状态机（TDD）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillLifecycleService.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillLifecycleServiceTest.java`

合法迁移：
- `DRAFT → PENDING_APPROVAL, ACTIVE, REJECTED`
- `PENDING_APPROVAL → ACTIVE, REJECTED`
- `ACTIVE → ARCHIVED`
- `REJECTED → DRAFT`
- `ARCHIVED → ACTIVE`

- [ ] **Step 1: 写失败测试 `SkillLifecycleServiceTest.java`**

```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
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
        assertThrows(IllegalStateException.class,
                () -> service.assertTransition(skill(SkillStatus.DRAFT), SkillStatus.ARCHIVED));
    }

    @Test
    void active_to_draft_is_forbidden() {
        assertThrows(IllegalStateException.class,
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl aicoder-skill -Dtest=SkillLifecycleServiceTest`
Expected: 编译失败 / 测试失败（`SkillLifecycleService` 不存在）。

- [ ] **Step 3: 实现 `SkillLifecycleService.java`**

```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 技能生命周期状态机：校验状态迁移合法性。
 * 迁移规则见本计划 Task 4 说明。物化副作用（写/删文件）由 SkillService 在迁移后协调，
 * 不放在这里，保持本类纯校验、可单测。
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
     * 校验从 skill 当前状态迁移到 target 是否合法；非法则抛 IllegalStateException。
     */
    public void assertTransition(Skill skill, SkillStatus target) {
        SkillStatus current = skill.getStatus();
        Set<SkillStatus> allowed = transitions.getOrDefault(current, EnumSet.noneOf(SkillStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    "非法的状态迁移：%s -> %s（技能 %s）".formatted(current, target, skill.getName()));
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl aicoder-skill -Dtest=SkillLifecycleServiceTest`
Expected: 8 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillLifecycleService.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillLifecycleServiceTest.java
git commit -m "feat(skill): 技能生命周期状态机（TDD）"
```

---

### Task 5: SkillRegistrySyncService 物化（TDD）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillRegistrySyncService.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillRegistrySyncServiceTest.java`

职责：ACTIVE 时把 `content` 写到 `{directory}/{name}/SKILL.md` 并返回该相对路径；删除时移除该目录。

- [ ] **Step 1: 写失败测试 `SkillRegistrySyncServiceTest.java`**

```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistrySyncServiceTest {

    @TempDir
    Path tempDir;

    private SkillRegistrySyncService service;

    @BeforeEach
    void setUp() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        service = new SkillRegistrySyncService(props);
    }

    private Skill skill(String name, String content) {
        Skill s = new Skill();
        s.setName(name);
        s.setContent(content);
        return s;
    }

    @Test
    void materialize_writes_skill_md_with_content() throws Exception {
        Skill s = skill("pdf-extractor", "---\nname: pdf-extractor\n---\n正文");

        String relPath = service.materialize(s);

        Path file = tempDir.resolve(relPath);
        assertEquals("pdf-extractor/SKILL.md", relPath);
        assertTrue(Files.exists(file));
        assertEquals("---\nname: pdf-extractor\n---\n正文", Files.readString(file));
    }

    @Test
    void materialize_overwrites_existing_file() throws Exception {
        service.materialize(skill("pdf-extractor", "v1"));
        service.materialize(skill("pdf-extractor", "v2"));

        Path file = tempDir.resolve("pdf-extractor/SKILL.md");
        assertEquals("v2", Files.readString(file));
    }

    @Test
    void remove_deletes_skill_directory() throws Exception {
        service.materialize(skill("pdf-extractor", "内容"));
        assertTrue(Files.exists(tempDir.resolve("pdf-extractor/SKILL.md")));

        service.remove("pdf-extractor");

        assertFalse(Files.exists(tempDir.resolve("pdf-extractor")));
    }

    @Test
    void remove_is_idempotent_when_missing() {
        // 目录不存在时不应抛异常
        service.remove("does-not-exist");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl aicoder-skill -Dtest=SkillRegistrySyncServiceTest`
Expected: 失败（类不存在）。

- [ ] **Step 3: 实现 `SkillRegistrySyncService.java`**

```java
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
        return Paths.get(skillProperties.getDirectory(), skillName);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl aicoder-skill -Dtest=SkillRegistrySyncServiceTest`
Expected: 4 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillRegistrySyncService.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillRegistrySyncServiceTest.java
git commit -m "feat(skill): SkillRegistrySyncService 物化/删除 SKILL.md（TDD）"
```

---

### Task 6: SkillDTO

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/dto/SkillDTO.java`

- [ ] **Step 1: 创建 `SkillDTO.java`**

```java
package com.ai.coder.skill.dto;

import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SkillDTO {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String content;
    private Integer version;
    private SkillStatus status;
    private SkillSource source;
    private String category;
    private String tags;
    private BigDecimal qualityScore;
    private String trialResult;
    private Long parentSkillId;
    private Long authorUserId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/dto/SkillDTO.java
git commit -m "feat(skill): SkillDTO"
```

---

### Task 7: SkillService（CRUD + 审批编排，TDD）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`

职责：create/list/get/update/delete/approve/reject/archive/activate。审批/激活后调用 sync 物化；归档后调用 sync 删除。

- [ ] **Step 1: 写失败测试 `SkillServiceTest.java`**

```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.dto.SkillDTO;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        sync = new SkillRegistrySyncService(props);
        lifecycle = new SkillLifecycleService();
        service = new SkillService(repository, sync, lifecycle);

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
        assertThrows(IllegalStateException.class, () -> service.create(draftDto(), 99L));
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
    void approve_rejects_illegal_transition_from_archived() {
        Skill s = persisted(SkillStatus.ARCHIVED);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThrows(IllegalStateException.class, () -> service.approve(1L, 7L));
        // 不应物化
        verify(repository, never()).save(any(Skill.class));
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
        syncService.materialize(s); // 先物化，模拟此前审批通过的状态
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        Skill result = service.archive(1L);

        assertEquals(SkillStatus.ARCHIVED, result.getStatus());
        assertFalse(Files.exists(tempDir.resolve("pdf-extractor/SKILL.md")));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl aicoder-skill -Dtest=SkillServiceTest`
Expected: 失败（`SkillService` 不存在）。

- [ ] **Step 3: 实现 `SkillService.java`**

```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.dto.SkillDTO;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillRegistrySyncService syncService;
    private final SkillLifecycleService lifecycleService;

    @Transactional
    public Skill create(SkillDTO dto, Long authorUserId) {
        if (skillRepository.existsByName(dto.getName())) {
            throw new IllegalStateException("技能名已存在：" + dto.getName());
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

    public List<Skill> listAll() {
        return skillRepository.findAll();
    }

    public List<Skill> listPending() {
        return skillRepository.findByStatusOrderByNameAsc(SkillStatus.PENDING_APPROVAL);
    }

    public Skill getById(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("技能不存在：" + id));
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
        Skill saved = skillRepository.save(skill);
        syncService.remove(skill.getName());
        saved.setFilePath(null);
        return skillRepository.save(saved);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl aicoder-skill -Dtest=SkillServiceTest`
Expected: 7 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java
git commit -m "feat(skill): SkillService CRUD + 审批/归档编排（TDD）"
```

---

### Task 8: Controller（REST `/api/skill/**`）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java`

- [ ] **Step 1: 创建 `SkillController.java`**

```java
package com.ai.coder.skill.controller;

import com.ai.coder.skill.dto.SkillDTO;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public ResponseEntity<List<Skill>> list() {
        return ResponseEntity.ok(skillService.listAll());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Skill>> listPending() {
        return ResponseEntity.ok(skillService.listPending());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Skill> getById(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.getById(id));
    }

    @PostMapping
    public ResponseEntity<Skill> create(@RequestBody SkillDTO dto,
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(skillService.create(dto, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Skill> update(@PathVariable Long id, @RequestBody SkillDTO dto) {
        return ResponseEntity.ok(skillService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/submit")
    public ResponseEntity<Skill> submitForApproval(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.submitForApproval(id));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Skill> approve(@PathVariable Long id,
                                         @RequestHeader(value = "X-User-Id", required = false) Long approverId) {
        return ResponseEntity.ok(skillService.approve(id, approverId));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Skill> reject(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.reject(id));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Skill> archive(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.archive(id));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl aicoder-skill -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java
git commit -m "feat(skill): SkillController REST 接口 /api/skill/**"
```

---

### Task 9: 网关路由 + 启动播种 + 端到端编译

**Files:**
- Modify: `aicoder-gateway/src/main/resources/application.yml`（加 `skill-service` 路由）
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/init/SkillDataInitializer.java`

- [ ] **Step 1: 网关增加 skill-service 路由**

在 `aicoder-gateway/src/main/resources/application.yml` 的 `system-service` 路由块之后，新增：

```yaml
        - id: skill-service
          uri: lb://aicoder-skill
          predicates:
            - Path=/api/skill/**
```

注意 YAML 缩进与同级路由一致（4 空格 `- id:`，6 空格子键）。

- [ ] **Step 2: 创建 `SkillDataInitializer.java`**（启动时播种一个示例 ACTIVE 技能，验证物化端到端）

```java
package com.ai.coder.skill.init;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import com.ai.coder.skill.service.SkillRegistrySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class SkillDataInitializer implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final SkillRegistrySyncService syncService;

    @Override
    public void run(String... args) {
        if (skillRepository.existsByName("greeting-skill")) {
            return;
        }
        Skill skill = new Skill();
        skill.setName("greeting-skill");
        skill.setDisplayName("问候技能");
        skill.setDescription("当用户打招呼或寒暄时使用，以友好的方式回应。");
        skill.setContent("""
                ---
                name: greeting-skill
                description: 当用户打招呼或寒暄时使用，以友好的方式回应。
                ---
                # 问候技能
                遇到问候时，先简短回应，再询问可以帮什么忙。保持语气友好、简洁。
                """);
        skill.setVersion(1);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setSource(SkillSource.MANUAL);
        skill.setCreatedAt(java.time.LocalDateTime.now());
        skill.setUpdatedAt(java.time.LocalDateTime.now());
        Skill saved = skillRepository.save(skill);
        saved.setFilePath(syncService.materialize(saved));
        skillRepository.save(saved);
        log.info("已播种示例技能 greeting-skill 并物化到 {}", saved.getFilePath());
    }
}
```

- [ ] **Step 3: 全模块测试 + 编译**

Run: `mvn clean test -pl aicoder-skill`
Expected: BUILD SUCCESS，全部单元测试通过。

- [ ] **Step 4: 提交**

```bash
git add aicoder-gateway/src/main/resources/application.yml aicoder-skill/src/main/java/com/ai/coder/skill/init/SkillDataInitializer.java
git commit -m "feat(skill): 网关 /api/skill/** 路由 + 启动播种示例技能"
```

---

### Task 10: 手动端到端验证（需中间件运行）

> 本任务需 MySQL/Redis/Nacos/Gateway 运行。无自动化测试，记录验证结果。

- [ ] **Step 1: 初始化数据库表**

在 MySQL `test_ai` 执行 `sql/schema.sql` 中新增的 `ai_skill` 建表语句（或依赖 Hibernate ddl-auto 自动建表）。

- [ ] **Step 2: 启动顺序**

依次启动：Nacos/MySQL/Redis → gateway (:8080) → aicoder-skill (:8086)。

- [ ] **Step 3: 验证播种与物化**

Run: `ls skills/greeting-skill/SKILL.md`
Expected: 文件存在，内容含 `name: greeting-skill`。

- [ ] **Step 4: 经网关调用 API（需有效 JWT）**

```bash
# 用 admin 登录拿 token（参考项目现有登录流程）
TOKEN=<填入 JWT>

# 列表应含 greeting-skill
curl -s http://localhost:8080/api/skill -H "Authorization: Bearer $TOKEN"

# 新建 → 提交 → 审批
curl -s -X POST http://localhost:8080/api/skill -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"faq-skill","description":"回答常见问题","content":"---\nname: faq-skill\n---\nFAQ 技能正文"}'

curl -s -X PUT http://localhost:8080/api/skill/<id>/approve -H "Authorization: Bearer $TOKEN"
```

Expected: 审批后 `skills/faq-skill/SKILL.md` 被创建。

- [ ] **Step 5: 记录验证结果并提交（如有修复）**

如端到端通过，无需提交；如有问题修复后提交。

---

## Self-Review 结论

- **Spec 覆盖**：Phase 1 范围（管理面 CRUD、状态机、审批、物化、网关路由）全部有对应任务。Phase 2/3（chat 运行面、生成闭环、前端页）显式划为本计划之外，后续独立计划。
- **占位符**：无 TBD/TODO；所有代码步骤含完整代码。
- **类型一致**：`SkillStatus`/`SkillSource` 枚举、`Skill` 字段、`SkillDTO` 字段、`SkillService` 方法签名、`SkillController` 路径全文一致；`SkillRegistrySyncService.materialize` 返回 `{name}/SKILL.md` 与 `SkillService.approve`/`archive` 用法一致。
- **关键风险**：`sql/schema.sql` 已加 `ai_skill`；共享目录 `./skills` 默认值在 application.yml 与各运行面后续配置需保持一致（Phase 2 会统一）。

---

## 后续计划（不在本计划内）

- **Phase 2**：chat 运行面接入——为 chat 加 `spring-ai-alibaba-graph-core` 依赖，用 `FileSystemSkillRegistry` + `SkillPromptAugmentAdvisor` + `ReadSkillTool`；调研 chat 现有自定义工具分发机制后注册 `submit_skill_draft`。
- **Phase 3**：元技能生成闭环——`SkillGenerationService`（复用 DynamicModelRegistry 模式）、`SkillQualityScorer`、`generate-skill` 元技能内容、质量闸门。
- **前端**：aicoder-web 技能管理页（列表/编辑/审批队列）。
