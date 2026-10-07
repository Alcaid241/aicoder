# Skill 管理面验证修复实现计划（Nacos 共享配置 + 错误码语义清理）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Phase 1 管理面端到端验证暴露的两个问题——把 `skill.directory` 提升为 Nacos 共享配置并解析为绝对路径，把错误码模型重建为精确的 404/409/500。

**Architecture:** Fix 2（错误码）引入 3 个领域异常 + 重构 `GlobalExceptionHandler` 为精确映射，调用点改抛新异常；Fix 1（路径）给 `SkillProperties` 加 `getResolvedDirectory()`（相对→绝对）+ 启动日志，再接入 Nacos config（`spring.config.import` + `@RefreshScope`），让 `skill.directory` 成为跨服务共享真相源。两者均 TDD，互不依赖。

**Tech Stack:** Spring Boot 3.5 / Spring Cloud 2025.0.0 / Spring Cloud Alibaba 2025.0.0.0 / JPA / JUnit 5 + Mockito / Nacos。

**关联设计：** [docs/superpowers/specs/2026-06-14-skill-config-nacos-and-error-codes-design.md](../specs/2026-06-14-skill-config-nacos-and-error-codes-design.md)

**范围边界（本计划不做）：** chat 运行面接入（Phase 2）、Controller 返回类型改 `SkillDTO`、已 ACTIVE 技能在路径变更后的批量迁移。

---

## File Structure

**Create:**
- `aicoder-skill/src/main/java/com/ai/coder/skill/exception/SkillNotFoundException.java`
- `aicoder-skill/src/main/java/com/ai/coder/skill/exception/DuplicateSkillException.java`
- `aicoder-skill/src/main/java/com/ai/coder/skill/exception/IllegalSkillStateException.java`
- `aicoder-skill/src/test/java/com/ai/coder/skill/config/GlobalExceptionHandlerTest.java`
- `aicoder-skill/src/test/java/com/ai/coder/skill/config/SkillPropertiesTest.java`

**Modify:**
- `aicoder-skill/src/main/java/com/ai/coder/skill/config/GlobalExceptionHandler.java`（重构映射）
- `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillLifecycleService.java`（`assertTransition` 抛新异常）
- `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java`（`getById`/`create` 抛新异常）
- `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillLifecycleServiceTest.java`（断言类型）
- `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`（断言类型 + 新增 not-found 用例）
- `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java`（`getResolvedDirectory` + 日志 + `@RefreshScope`）
- `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillRegistrySyncService.java`（用 `getResolvedDirectory`）
- `aicoder-skill/pom.xml`（加 `nacos-config` 依赖）
- `aicoder-skill/src/main/resources/application.yml`（`spring.config.import` + nacos server-addr + env 兜底）

---

### Task 1: 领域异常类 + GlobalExceptionHandler 重构（TDD）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/exception/SkillNotFoundException.java`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/exception/DuplicateSkillException.java`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/exception/IllegalSkillStateException.java`
- Create: `aicoder-skill/src/test/java/com/ai/coder/skill/config/GlobalExceptionHandlerTest.java`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/config/GlobalExceptionHandler.java`

- [ ] **Step 1: 写失败测试 `GlobalExceptionHandlerTest.java`**

```java
package com.ai.coder.skill.config;

import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import com.ai.coder.skill.exception.SkillNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> r) {
        return (Map<String, Object>) r.getBody();
    }

    @Test
    void skill_not_found_maps_404() {
        ResponseEntity<?> r = handler.handleNotFound(new SkillNotFoundException("技能不存在：999"));

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals(404, body(r).get("code"));
        assertEquals("技能不存在：999", body(r).get("message"));
    }

    @Test
    void duplicate_skill_maps_409() {
        ResponseEntity<?> r = handler.handleConflict(new DuplicateSkillException("技能名已存在：faq-skill"));

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(409, body(r).get("code"));
        assertEquals("技能名已存在：faq-skill", body(r).get("message"));
    }

    @Test
    void illegal_state_transition_maps_409() {
        ResponseEntity<?> r = handler.handleConflict(new IllegalSkillStateException("非法的状态迁移：ACTIVE -> ACTIVE"));

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(409, body(r).get("code"));
    }

    @Test
    void unexpected_exception_maps_500_with_generic_message() {
        ResponseEntity<?> r = handler.handleUnexpected(new NullPointerException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals(500, body(r).get("code"));
        // 内部异常消息不得外泄，统一返回通用文案
        assertEquals("服务内部错误", body(r).get("message"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl aicoder-skill -Dtest=GlobalExceptionHandlerTest`（环境无 mvn 时用 `/Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`，并 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`）
Expected: 编译失败（异常类与 handler 方法均不存在）。

- [ ] **Step 3: 创建 3 个异常类**

`SkillNotFoundException.java`：

```java
package com.ai.coder.skill.exception;

/** 技能不存在，映射 HTTP 404。 */
public class SkillNotFoundException extends RuntimeException {
    public SkillNotFoundException(String message) {
        super(message);
    }
}
```

`DuplicateSkillException.java`：

```java
package com.ai.coder.skill.exception;

/** 技能名重复，映射 HTTP 409 Conflict。 */
public class DuplicateSkillException extends RuntimeException {
    public DuplicateSkillException(String message) {
        super(message);
    }
}
```

`IllegalSkillStateException.java`：

```java
package com.ai.coder.skill.exception;

/** 非法的生命周期状态迁移，映射 HTTP 409 Conflict。 */
public class IllegalSkillStateException extends RuntimeException {
    public IllegalSkillStateException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: 重构 `GlobalExceptionHandler.java`（整体替换）**

```java
package com.ai.coder.skill.config;

import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import com.ai.coder.skill.exception.SkillNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(SkillNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler({DuplicateSkillException.class, IllegalSkillStateException.class})
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        // 内部异常细节不得外泄，统一返回通用文案
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务内部错误"));
    }

    private static Map<String, Object> body(int code, String message) {
        return Map.of("code", code, "message", message != null ? message : "未知错误");
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -pl aicoder-skill -Dtest=GlobalExceptionHandlerTest`
Expected: 4 个测试全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/exception/ aicoder-skill/src/main/java/com/ai/coder/skill/config/GlobalExceptionHandler.java aicoder-skill/src/test/java/com/ai/coder/skill/config/GlobalExceptionHandlerTest.java
git commit -m "feat(skill): 领域异常 + GlobalExceptionHandler 精确映射 404/409/500（TDD）"
```

---

### Task 2: 调用点接入新异常 + 现有测试更新（TDD）

**Files:**
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillLifecycleService.java`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java`
- Modify: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillLifecycleServiceTest.java`
- Modify: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`

- [ ] **Step 1: 改 `SkillLifecycleServiceTest` 两处断言类型**

把 `IllegalStateException.class` 改为 `IllegalSkillStateException.class`，并补 import。两处分别为 `draft_to_archived_is_forbidden`、`active_to_draft_is_forbidden`。

文件顶部 import 区，把：

```java
import static org.junit.jupiter.api.Assertions.assertThrows;
```

下面新增一行（与现有 import 并列）：

```java
import com.ai.coder.skill.exception.IllegalSkillStateException;
```

`draft_to_archived_is_forbidden`：

```java
    @Test
    void draft_to_archived_is_forbidden() {
        assertThrows(IllegalSkillStateException.class,
                () -> service.assertTransition(skill(SkillStatus.DRAFT), SkillStatus.ARCHIVED));
    }
```

`active_to_draft_is_forbidden`：

```java
    @Test
    void active_to_draft_is_forbidden() {
        assertThrows(IllegalSkillStateException.class,
                () -> service.assertTransition(skill(SkillStatus.ACTIVE), SkillStatus.DRAFT));
    }
```

- [ ] **Step 2: 改 `SkillServiceTest` 断言类型 + 新增 not-found 用例**

文件顶部 import 区新增：

```java
import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import com.ai.coder.skill.exception.SkillNotFoundException;
```

`create_rejects_duplicate_name` 改为：

```java
    @Test
    void create_rejects_duplicate_name() {
        when(repository.existsByName("pdf-extractor")).thenReturn(true);
        assertThrows(DuplicateSkillException.class, () -> service.create(draftDto(), 99L));
    }
```

`submit_rejects_illegal_transition_from_active` 改为：

```java
    @Test
    void submit_rejects_illegal_transition_from_active() {
        Skill s = persisted(SkillStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThrows(IllegalSkillStateException.class, () -> service.submitForApproval(1L));
        verify(repository, never()).save(any(Skill.class));
    }
```

`approve_rejects_illegal_transition_from_rejected` 改为：

```java
    @Test
    void approve_rejects_illegal_transition_from_rejected() {
        // REJECTED 只允许 -> DRAFT，不可直接 -> ACTIVE；被拒技能须先回到 DRAFT 重新提审
        Skill s = persisted(SkillStatus.REJECTED);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThrows(IllegalSkillStateException.class, () -> service.approve(1L, 7L));
        verify(repository, never()).save(any(Skill.class));
    }
```

在 `listAll_returns_all` 之前新增 not-found 用例：

```java
    @Test
    void getById_not_found_throws_SkillNotFoundException() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(SkillNotFoundException.class, () -> service.getById(404L));
    }
```

- [ ] **Step 3: 运行测试确认失败（红）**

Run: `mvn test -pl aicoder-skill -Dtest=SkillLifecycleServiceTest,SkillServiceTest`
Expected: 失败——服务仍抛 `IllegalStateException`/`RuntimeException`，新断言类型不匹配；`getById_not_found` 期望 `SkillNotFoundException` 实际 `RuntimeException`。

- [ ] **Step 4: 改 `SkillLifecycleService.assertTransition` 抛新异常**

把 `assertTransition` 内的 `throw new IllegalStateException(...)` 改为 `throw new IllegalSkillStateException(...)`，并补 import。

文件顶部 import 区新增：

```java
import com.ai.coder.skill.exception.IllegalSkillStateException;
```

`assertTransition` 方法整体替换为：

```java
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
```

- [ ] **Step 5: 改 `SkillService.getById` 与 `create` 抛新异常**

文件顶部 import 区新增：

```java
import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.SkillNotFoundException;
```

`create` 中重名分支（`if (skillRepository.existsByName(dto.getName()))`）改为：

```java
        if (skillRepository.existsByName(dto.getName())) {
            throw new DuplicateSkillException("技能名已存在：" + dto.getName());
        }
```

`getById` 整体替换为：

```java
    public Skill getById(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("技能不存在：" + id));
    }
```

- [ ] **Step 6: 运行全部 skill 测试确认通过（绿）**

Run: `mvn test -pl aicoder-skill`
Expected: 全部 PASS（SkillLifecycleServiceTest 8、SkillRegistrySyncServiceTest 6、SkillServiceTest 13、GlobalExceptionHandlerTest 4）。

- [ ] **Step 7: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillLifecycleService.java aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillLifecycleServiceTest.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java
git commit -m "feat(skill): 调用点接入领域异常，错误码 404/409 端到端生效（TDD）"
```

---

### Task 3: skill.directory 解析为绝对路径 + 启动日志（TDD）

**Files:**
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillRegistrySyncService.java`
- Create: `aicoder-skill/src/test/java/com/ai/coder/skill/config/SkillPropertiesTest.java`

- [ ] **Step 1: 写失败测试 `SkillPropertiesTest.java`**

```java
package com.ai.coder.skill.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPropertiesTest {

    @Test
    void resolved_directory_is_absolute() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("./skills");

        String resolved = props.getResolvedDirectory();

        assertTrue(new File(resolved).isAbsolute(), "解析后应为绝对路径");
        // 原始配置值保持不变（相对路径）
        assertEquals("./skills", props.getDirectory());
    }

    @Test
    void resolved_directory_reflects_set_value() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("/tmp/some-where");

        assertEquals("/tmp/some-where", props.getResolvedDirectory());
    }

    @Test
    void env_override_value_is_kept() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(System.getProperty("java.io.tmpdir"));

        String resolved = props.getResolvedDirectory();
        assertEquals(System.getProperty("java.io.tmpdir"), resolved);
        // 与默认值不同，证明注入生效
        assertNotEquals("./skills", resolved);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl aicoder-skill -Dtest=SkillPropertiesTest`
Expected: 编译失败（`getResolvedDirectory()` 不存在）。

- [ ] **Step 3: 改 `SkillProperties.java`（整体替换）**

```java
package com.ai.coder.skill.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;

@Slf4j
@Data
@ConfigurationProperties(prefix = "skill")
public class SkillProperties {

    /**
     * 技能物化的共享目录，所有运行面服务的 SkillRegistry 指向同一目录。
     * 可为相对路径（相对 JVM 工作目录）或绝对路径；实际使用前用 {@link #getResolvedDirectory()} 解析为绝对路径。
     */
    private String directory = "./skills";

    /**
     * 把 {@link #directory} 解析为绝对路径。相对路径相对 JVM 工作目录。
     */
    public String getResolvedDirectory() {
        return new File(directory).getAbsoluteFile().getPath();
    }

    @PostConstruct
    void logResolvedDirectory() {
        // 高亮实际落盘路径，让 cwd 混淆在启动日志即可发现
        log.warn("[skill] directory: configured='{}' resolved='{}'", directory, getResolvedDirectory());
    }
}
```

- [ ] **Step 4: 改 `SkillRegistrySyncService` 使用 `getResolvedDirectory`**

把 `resolveSkillDir` 方法中的 `Paths.get(skillProperties.getDirectory(), skillName)` 改为 `Paths.get(skillProperties.getResolvedDirectory(), skillName)`。该方法整体替换为：

```java
    private Path resolveSkillDir(String skillName) {
        return Paths.get(skillProperties.getResolvedDirectory(), skillName);
    }
```

（其余 `SkillRegistrySyncService` 不变。）

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -pl aicoder-skill -Dtest=SkillPropertiesTest,SkillRegistrySyncServiceTest,SkillServiceTest`
Expected: 全部 PASS。`SkillRegistrySyncServiceTest`/`SkillServiceTest` 用 `@TempDir`（已是绝对路径）注入，`getResolvedDirectory()` 对绝对输入是幂等，行为不变。

- [ ] **Step 6: Commit**

```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillRegistrySyncService.java aicoder-skill/src/test/java/com/ai/coder/skill/config/SkillPropertiesTest.java
git commit -m "feat(skill): directory 解析为绝对路径 + 启动日志，cwd 混淆可见（TDD）"
```

---

### Task 4: Nacos config 接入（共享配置 + 动态刷新）

**Files:**
- Modify: `aicoder-skill/pom.xml`
- Modify: `aicoder-skill/src/main/resources/application.yml`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java`

- [ ] **Step 1: pom 加 `nacos-config` 依赖**

在 `aicoder-skill/pom.xml` 中 `spring-cloud-starter-alibaba-nacos-discovery` 依赖块（当前第 43–46 行）之后，新增：

```xml
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
```

（版本由父 BOM `spring-cloud-alibaba-dependencies 2025.0.0.0` 管理，无需显式 version。）

- [ ] **Step 2: `application.yml` 加 config-import 与 nacos server-addr，skill.directory 改 env 兜底**

把 `application.yml` 的 `spring:` 块与 `skill:` 块整体替换为（保留 `server:`、`springdoc:` 不动）：

```yaml
spring:
  application:
    name: aicoder-skill
  config:
    import:
      # Nacos 共享配置：未来 chat 引入同一 data-id 即读到同一 skill.directory。
      # optional: 前缀保证 Nacos 不可用时服务仍可启动（退回本地默认）。
      - optional:nacos:aicoder-shared.yml
  cloud:
    nacos:
      server-addr: localhost:8848
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

# 技能物化共享目录：本地兜底默认值，可被 Nacos aicoder-shared.yml 或 SKILL_DIRECTORY 环境变量覆盖。
# 推荐在 Nacos 中配置为所有运行面共用的绝对路径。
skill:
  directory: ${SKILL_DIRECTORY:./skills}

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

> 配置优先级：Nacos `aicoder-shared.yml` 中的 `skill.directory` > 本地 `application.yml`（含 `${SKILL_DIRECTORY:./skills}`）。

- [ ] **Step 3: `SkillProperties` 加 `@RefreshScope`**

文件顶部 import 区新增：

```java
import org.springframework.cloud.context.config.annotation.RefreshScope;
```

类注解区，把：

```java
@Slf4j
@Data
@ConfigurationProperties(prefix = "skill")
public class SkillProperties {
```

改为：

```java
@Slf4j
@RefreshScope
@Data
@ConfigurationProperties(prefix = "skill")
public class SkillProperties {
```

（`@RefreshScope` 使 Nacos 修改 `skill.directory` 后，新审批激活的技能物化到新路径。已知限制：不重物化已 ACTIVE 技能。）

- [ ] **Step 4: 编译 + 运行全部单元测试（无 Nacos 仍应通过）**

Run: `mvn clean test -pl aicoder-skill`
Expected: BUILD SUCCESS，全部单元测试 PASS。`optional:` 前缀保证 Nacos 缺席时本地默认生效；单元测试不启动 Nacos，`@RefreshScope` 在非 Web/无配置中心的纯单测中以普通 bean 行为运行。

- [ ] **Step 5: Commit**

```bash
git add aicoder-skill/pom.xml aicoder-skill/src/main/resources/application.yml aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java
git commit -m "feat(skill): 接入 Nacos 共享配置 aicoder-shared.yml + @RefreshScope 动态刷新"
```

---

### Task 5: 端到端回归（需中间件运行）

> 本任务需 MySQL/Redis/Nacos/Gateway/Skill 运行。无自动化测试，记录验证结果。环境无 mvn 时统一用 `/Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`，`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`。

- [ ] **Step 1: 构建 gateway + skill**

Run: `mvn clean install -pl aicoder-gateway,aicoder-skill -am -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 2: 在 Nacos 创建共享配置 `aicoder-shared.yml`**

用 Nacos OpenAPI 创建（指定一个与本地默认不同的绝对路径，以证明 Nacos 值生效）：

```bash
curl -s -X POST 'http://localhost:8848/nacos/v1/cs/configs' \
  --data-urlencode 'dataId=aicoder-shared.yml' \
  --data-urlencode 'group=DEFAULT_GROUP' \
  --data-urlencode 'type=yaml' \
  --data-urlencode "content=skill:
  directory: /Users/haijingxu/workspace/claudeCode/aicoder/skills"
```
Expected: 输出 `true`。若返回 403（开启了鉴权），先 `curl -s 'http://localhost:8848/nacos/v1/auth/login' -d 'username=nacos&password=nacos'` 取 `accessToken`，再在创建请求里追加 `--data-urlencode "accessToken=<token>"`。

校验已写入：

```bash
curl -s 'http://localhost:8848/nacos/v1/cs/configs?dataId=aicoder-shared.yml&group=DEFAULT_GROUP'
```
Expected: 返回 `skill:\n  directory: ...`。

- [ ] **Step 3: 启动 gateway + skill**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
nohup $JAVA_HOME/bin/java -jar aicoder-gateway/target/aicoder-gateway-1.0.0-SNAPSHOT.jar > /tmp/aicoder_gw.log 2>&1 &
sleep 2
nohup $JAVA_HOME/bin/java -jar aicoder-skill/target/aicoder-skill-1.0.0-SNAPSHOT.jar > /tmp/aicoder_skill.log 2>&1 &
```
等待 ~15s，确认 :8080 与 :8086 端口 OPEN、skill 注册 Nacos 成功。

- [ ] **Step 4: 验证 directory 来自 Nacos（启动日志）**

```bash
grep "\[skill\] directory" /tmp/aicoder_skill.log
```
Expected: 一行 `WARN ... [skill] directory: configured='<Nacos 值>' resolved='<绝对路径>'`，其中 configured 与 resolved 均为 Step 2 写入的 `/Users/haijingxu/workspace/claudeCode/aicoder/skills`（即 Nacos 值，而非本地默认 `./skills`）。**这是 Nacos config 接线成功的判据。**

- [ ] **Step 5: 验证错误码（404 / 409 / 500 语义）经网关生效**

mint 一个 JWT（参考验证阶段做法，HMAC256，secret=`aicoder-jwt-secret-key-2024-must-be-at-least-256-bits-long`，claims `userId=1,username=admin`），然后：

```bash
TOKEN=<JWT>

# 未命中 -> 404
curl -s -o /dev/null -w "not-found HTTP %{http_code}\n" http://localhost:8080/api/skill/999 -H "Authorization: Bearer $TOKEN"
# Expected: HTTP 404

# 重名 -> 409（greeting-skill 已存在）
curl -s -o /dev/null -w "duplicate HTTP %{http_code}\n" -X POST http://localhost:8080/api/skill \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"greeting-skill","description":"d","content":"x"}'
# Expected: HTTP 409

# 新建一个再非法审批 -> 409
curl -s -o /tmp/s.json -X POST http://localhost:8080/api/skill -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"e2e-probe","description":"d","content":"---\nname: e2e-probe\n---\nx"}'
ID=$(python3 -c "import json;print(json.load(open('/tmp/s.json'))['id'])")
curl -s -o /dev/null -w "illegal-approve HTTP %{http_code}\n" -X PUT http://localhost:8080/api/skill/$ID/approve -H "Authorization: Bearer $TOKEN"
# DRAFT -> ACTIVE 合法，应是 200；再 approve 一次 ACTIVE -> ACTIVE 非法 -> 409
curl -s -o /dev/null -w "illegal-approve-2 HTTP %{http_code}\n" -X PUT http://localhost:8080/api/skill/$ID/approve -H "Authorization: Bearer $TOKEN"
# Expected: illegal-approve-2 HTTP 409
```

- [ ] **Step 6: 验证物化落在 Nacos 配置的绝对路径**

```bash
ls /Users/haijingxu/workspace/claudeCode/aicoder/skills/e2e-probe/SKILL.md && echo OK
```
Expected: 文件存在（即审批激活后物化到 Nacos 配置的绝对路径）。

- [ ] **Step 7: 清理 + 记录**

```bash
# 停服务
pkill -f "aicoder-gateway-1.0.0-SNAPSHOT.jar"; pkill -f "aicoder-skill-1.0.0-SNAPSHOT.jar"
# 删测试技能行（保留 greeting-skill 种子）
docker exec mysql mysql -uroot -proot test_ai -e "DELETE FROM ai_skill WHERE name='e2e-probe';"
# 可选：删物化测试文件
rm -rf /Users/haijingxu/workspace/claudeCode/aicoder/skills/e2e-probe
```
如全部通过，无需提交；如发现问题修复后提交并说明。

---

## Self-Review 结论

- **Spec 覆盖**：
  - Fix 2（错误码 404/409/500 + 统一体）→ Task 1（异常 + handler）、Task 2（调用点 + 测试）、Task 5 Step 5（e2e 验证）。✅
  - Fix 1（绝对路径解析 + 日志）→ Task 3。✅
  - Fix 1（Nacos 共享配置 + env 兜底 + `@RefreshScope`）→ Task 4、Task 5 Step 2/4/6（创建 data-id、日志验证、物化路径验证）。✅
  - 已知限制（刷新不重物化）→ Task 4 Step 3 注释。✅
- **占位符**：无 TBD/TODO；所有代码步骤含完整代码，命令含期望输出。
- **类型一致**：`SkillNotFoundException`/`DuplicateSkillException`/`IllegalSkillStateException` 三个类名在 Task 1（定义）、Task 2（抛出+断言）、Task 5（e2e）全文一致；`getResolvedDirectory()` 在 Task 3（定义+测试）、Task 3 Step 4（消费）一致；handler 方法名 `handleNotFound`/`handleConflict`/`handleUnexpected` 与 Task 1 测试调用一致。
- **关键风险**：Nacos config 是本项目首个 config 接入点——Task 5 Step 4（启动日志判据）专门覆盖；`optional:` 前缀保证本地无 Nacos 仍可启动（Task 4 Step 4 验证）。
