# Agent Skill Phase 3：写侧自进化闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 chat 在判定无匹配技能时自主起草 SKILL.md 并经 `submit_skill_draft` 工具写回 aicoder-skill 审批队列，完成技能自进化写侧闭环。

**Architecture:** skill 侧新增启发式质量打分器 + `submitDraft` 写回端点（建 DRAFT→打分→阈值流转 PENDING/REJECTED）+ `generate-skill` 元技能种子；chat 侧新增 `submit_skill_draft` 工具（`@Tool` + `ToolContext` 透传 userId + `@LoadBalanced RestTemplate`），经 ChatClient 挂载。重名走 V1 版本后缀名（`-vN`），不改 schema。

**Tech Stack:** Spring Boot 3.5 / Spring AI 1.1.2（`@Tool`、`ToolContext`、`ChatClient`）/ Spring Cloud（`@LoadBalanced RestTemplate` + Nacos 发现）/ JPA + Mockito + JUnit 5 / Maven（路径 `/Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`，`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`）。

**Spec:** [2026-06-16-agent-skill-phase3-write-loop-design.md](../specs/2026-06-16-agent-skill-phase3-write-loop-design.md)

---

## File Structure

**skill 模块（`com.ai.coder.skill`）**
- Create `service/SkillQualityScorer.java` — 启发式打分纯函数（0–100）
- Create `service/ScoreResult.java` — `record(int score, String rationale)`
- Create `dto/SkillDraftRequest.java` — `{name, description, content}`
- Modify `config/SkillProperties.java` — 加 `qualityThreshold`（默认 60）
- Modify `service/SkillService.java` — 注入 `SkillProperties` + `SkillQualityScorer`；新增 `submitDraft(...)`
- Modify `controller/SkillController.java` — 加 `POST /api/skill/draft`
- Modify `init/SkillDataInitializer.java` — 重构为 `seedIfAbsent(...)`，加 `generate-skill` 元技能种子
- Test: `service/SkillQualityScorerTest.java`（新）、`service/SkillServiceSubmitDraftTest.java`（新）、`controller/SkillControllerDraftTest.java`（新）
- Test 修改: `service/SkillServiceTest.java`（构造函数加 2 参）、`init/SkillDataInitializerTest.java`（2 个种子）

**chat 模块（`com.ai.coder.chat`）**
- Create `tool/SubmitSkillDraftTool.java` — `@Tool submitSkillDraft(name, description, content, ToolContext)`
- Modify `service/ChatService.java` — 注入工具 + `defaultTools` 追加 + prompt 加 `.toolContext(userId)`
- Test: `tool/SubmitSkillDraftToolTest.java`（新）
- Test 修改: `service/ChatServiceHistoryTest.java`（构造函数加 1 参）

---

## Task 1: SkillQualityScorer 启发式打分器

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/service/ScoreResult.java`
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillQualityScorer.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillQualityScorerTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillQualityScorerTest.java`:

```java
package com.ai.coder.skill.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillQualityScorerTest {

    private final SkillQualityScorer scorer = new SkillQualityScorer();

    private static final String GOOD_CONTENT = """
            ---
            name: code-review
            description: 对代码变更进行清单式审查并给出修改建议。
            ---
            # Code Review
            当用户提交代码变更时，按以下清单逐项审查：可读性、命名、错误处理、测试覆盖、性能与安全。
            发现问题后给出具体的修改建议与示例代码，避免空泛评价。
            """;

    @Test
    void score_full_for_well_formed_draft() {
        ScoreResult r = scorer.score("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT);
        assertEquals(100, r.score(), "规范草稿应满分：" + r.rationale());
    }

    @Test
    void score_deducts_for_non_kebab_name() {
        ScoreResult r = scorer.score("Code_Review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT);
        assertEquals(75, r.score(), "非 kebab 名扣 25：" + r.rationale());
        assertTrue(r.rationale().contains("kebab"));
    }

    @Test
    void score_deducts_for_missing_or_invalid_frontmatter() {
        // 无 frontmatter：扣 25（正文仍足长，所以只扣这一项）
        String noFm = "# Code Review\n当用户提交代码变更时，按清单逐项审查可读性、命名、错误处理、测试覆盖与安全。\n";
        ScoreResult r = scorer.score("code-review",
                "对代码变更进行清单式审查并给出修改建议。", noFm);
        assertEquals(75, r.score(), "缺合法 frontmatter 扣 25：" + r.rationale());
    }

    @Test
    void score_deducts_for_short_body() {
        // 合法 frontmatter 但正文过短：扣 25
        String shortBody = "---\nname: code-review\ndescription: 审查代码。\n---\n短";
        ScoreResult r = scorer.score("code-review",
                "对代码变更进行清单式审查并给出修改建议。", shortBody);
        assertEquals(75, r.score(), "正文过短扣 25：" + r.rationale());
    }

    @Test
    void score_low_for_malformed_draft() {
        // 名字非 kebab + 描述过短 + 无 frontmatter + 正文过短 → 0
        ScoreResult r = scorer.score("Bad Name", "短", "x");
        assertEquals(0, r.score(), "四项全失应 0 分：" + r.rationale());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillQualityScorerTest
```
Expected: 编译失败（`SkillQualityScorer` / `ScoreResult` 不存在）。

- [ ] **Step 3: 实现 ScoreResult**

Create `aicoder-skill/src/main/java/com/ai/coder/skill/service/ScoreResult.java`:
```java
package com.ai.coder.skill.service;

/** 启发式打分结果：0–100 分 + 扣分原因。 */
public record ScoreResult(int score, String rationale) {
}
```

- [ ] **Step 4: 实现 SkillQualityScorer**

Create `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillQualityScorer.java`:
```java
package com.ai.coder.skill.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 启发式技能草稿质量打分（0–100），纯函数无 IO。四项各 25 分：
 * kebab 名格式 / 描述长度 10–200 / frontmatter 合法（成对 --- 且含 name+description）/ 正文（剥离 frontmatter 后）≥50 字。
 * 用于 submit_skill_draft 写回时的自动闸门：低于阈值的草稿直接 REJECTED，不进审批队列。
 */
@Component
public class SkillQualityScorer {

    private static final Pattern KEBAB_NAME = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
    private static final int MIN_DESC = 10;
    private static final int MAX_DESC = 200;
    private static final int MIN_BODY_LENGTH = 50;

    public ScoreResult score(String name, String description, String content) {
        List<String> notes = new ArrayList<>();
        int score = 0;

        if (name != null && KEBAB_NAME.matcher(name).matches()) {
            score += 25;
        } else {
            notes.add("名字非 kebab-case");
        }

        int descLen = description == null ? 0 : description.trim().length();
        if (descLen >= MIN_DESC && descLen <= MAX_DESC) {
            score += 25;
        } else {
            notes.add("描述长度应在 " + MIN_DESC + "-" + MAX_DESC + " 字");
        }

        if (hasValidFrontmatter(content)) {
            score += 25;
        } else {
            notes.add("缺少合法 frontmatter（成对 --- 且含 name/description）");
        }

        if (bodyLength(content) >= MIN_BODY_LENGTH) {
            score += 25;
        } else {
            notes.add("正文过短（< " + MIN_BODY_LENGTH + " 字）");
        }

        String rationale = notes.isEmpty() ? "全部通过" : String.join("；", notes);
        return new ScoreResult(score, rationale);
    }

    /** 剥离开头 frontmatter 后的正文长度；无合法 frontmatter 则按整段计。 */
    private int bodyLength(String content) {
        if (content == null) return 0;
        String stripped = content.stripLeading();
        if (!stripped.startsWith("---")) return content.trim().length();
        int end = stripped.indexOf("\n---", 3);
        if (end < 0) return content.trim().length();
        return stripped.substring(end + 4).trim().length();
    }

    private boolean hasValidFrontmatter(String content) {
        if (content == null) return false;
        String stripped = content.stripLeading();
        if (!stripped.startsWith("---")) return false;
        int end = stripped.indexOf("\n---", 3);
        if (end < 0) return false;
        String fm = stripped.substring(3, end);
        return fm.contains("name:") && fm.contains("description:");
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillQualityScorerTest
```
Expected: `Tests run: 5, Failures: 0`。

- [ ] **Step 6: 提交**
```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillQualityScorer.java aicoder-skill/src/main/java/com/ai/coder/skill/service/ScoreResult.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillQualityScorerTest.java
git commit -m "feat(skill): SkillQualityScorer 启发式草稿打分器（0-100）"
```

---

## Task 2: SkillService.submitDraft + qualityThreshold 配置

**Files:**
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceSubmitDraftTest.java`
- Test 修改: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`（构造函数加 2 参）

- [ ] **Step 1: 写失败测试**

Create `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceSubmitDraftTest.java`:
```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillServiceSubmitDraftTest {

    @TempDir
    Path tempDir;

    private SkillRepository repository;
    private SkillService service;

    private static final String GOOD_CONTENT = """
            ---
            name: code-review
            description: 对代码变更进行清单式审查并给出修改建议。
            ---
            # Code Review
            当用户提交代码变更时，按以下清单逐项审查：可读性、命名、错误处理、测试覆盖、性能与安全。
            发现问题后给出具体的修改建议与示例代码，避免空泛评价。
            """;

    @BeforeEach
    void setUp() {
        repository = mock(SkillRepository.class);
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        props.setQualityThreshold(60);
        // sync/lifecycle 在 submitDraft 不参与，传真实实例即可（无副作用）
        service = new SkillService(repository,
                new SkillRegistrySyncService(props),
                new SkillLifecycleService(),
                props,
                new SkillQualityScorer());
        when(repository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            if (s.getId() == null) s.setId(1L);
            return s;
        });
    }

    @Test
    void submitDraft_new_well_formed_becomes_pending() {
        when(repository.existsByName("code-review")).thenReturn(false);

        Skill result = service.submitDraft("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT, 99L);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        Skill saved = captor.getValue();
        assertEquals(SkillStatus.PENDING_APPROVAL, saved.getStatus(), "高分应进 PENDING");
        assertEquals(SkillSource.AUTO_GENERATED, saved.getSource());
        assertEquals(1, saved.getVersion());
        assertNull(saved.getParentSkillId());
        assertEquals(99L, saved.getAuthorUserId());
        assertEquals(100, saved.getQualityScore().intValue());
        assertEquals(SkillStatus.PENDING_APPROVAL, result.getStatus());
    }

    @Test
    void submitDraft_malformed_becomes_rejected() {
        when(repository.existsByName("bad name")).thenReturn(false);

        Skill result = service.submitDraft("bad name", "短", "x", null);

        assertEquals(SkillStatus.REJECTED, result.getStatus(), "低分应 REJECTED");
        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        assertEquals(0, captor.getValue().getQualityScore().intValue());
    }

    @Test
    void submitDraft_duplicate_name_versions_as_v2() {
        Skill original = new Skill();
        original.setId(5L);
        original.setName("code-review");
        original.setVersion(1);
        when(repository.existsByName("code-review")).thenReturn(true);
        when(repository.findByName("code-review")).thenReturn(Optional.of(original));
        when(repository.existsByName("code-review-v2")).thenReturn(false);

        service.submitDraft("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT, 99L);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(captor.capture());
        Skill saved = captor.getValue();
        assertEquals("code-review-v2", saved.getName(), "重名应版本后缀化");
        assertEquals(2, saved.getVersion());
        assertEquals(5L, saved.getParentSkillId());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillServiceSubmitDraftTest
```
Expected: 编译失败（`SkillService` 构造函数签名不匹配 / `submitDraft` 不存在 / `SkillProperties.getQualityThreshold` 不存在）。

- [ ] **Step 3: SkillProperties 加 qualityThreshold**

Modify `aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java` — 在 `directory` 字段后追加：
```java
    /**
     * 自动生成草稿的质量闸门阈值（0–100）。submit_skill_draft 写回的草稿打分 ≥ 此值才进 PENDING_APPROVAL，
     * 否则直接 REJECTED，防低质草稿淹没审批队列。经 Nacos aicoder-shared.yml 可热调（@RefreshScope）。
     */
    private int qualityThreshold = 60;
```
（`@Data` 自动生成 `getQualityThreshold()`/`setQualityThreshold(int)`。）

- [ ] **Step 4: SkillService 注入新依赖 + 实现 submitDraft**

Modify `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java`：

(a) 加 import：
```java
import com.ai.coder.skill.config.SkillProperties;
import java.math.BigDecimal;
```

(b) 加两个 final 字段（`@RequiredArgsConstructor` 自动并入构造函数）：
```java
    private final SkillProperties skillProperties;
    private final SkillQualityScorer qualityScorer;
```

(c) 新增方法（放在 `create(...)` 之后）：
```java
    /**
     * 自动生成草稿写回：重名版本后缀化（V1）→ 建 DRAFT(AUTO_GENERATED) → 启发式打分 →
     * 分数 ≥ 阈值转 PENDING_APPROVAL，否则 REJECTED。单事务。
     */
    @Transactional
    public Skill submitDraft(String name, String description, String content, Long authorUserId) {
        String finalName = name;
        int version = 1;
        Long parentSkillId = null;
        if (skillRepository.existsByName(name)) {
            Skill original = skillRepository.findByName(name)
                    .orElseThrow(() -> new IllegalStateException("技能存在但读取失败：" + name));
            parentSkillId = original.getId();
            version = nextFreeVersion(name, original.getVersion());
            finalName = name + "-v" + version;
        }

        Skill skill = new Skill();
        skill.setName(finalName);
        skill.setDescription(description);
        skill.setContent(content);
        skill.setVersion(version);
        skill.setStatus(SkillStatus.DRAFT);
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
```

- [ ] **Step 5: 修复既有 SkillServiceTest 构造函数调用**

Modify `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java` 的 `setUp()`——把：
```java
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        sync = new SkillRegistrySyncService(props);
        lifecycle = new SkillLifecycleService();
        service = new SkillService(repository, sync, lifecycle);
```
改为：
```java
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        props.setQualityThreshold(60);
        sync = new SkillRegistrySyncService(props);
        lifecycle = new SkillLifecycleService();
        service = new SkillService(repository, sync, lifecycle, props, new SkillQualityScorer());
```

- [ ] **Step 6: 运行测试确认通过（含既有 SkillServiceTest 回归）**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-skill test -Dtest='SkillServiceSubmitDraftTest,SkillServiceTest'
```
Expected: 两个类全绿（SkillServiceSubmitDraftTest 3 个 + SkillServiceTest 既有 13 个）。

- [ ] **Step 7: 提交**
```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/config/SkillProperties.java aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceSubmitDraftTest.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java
git commit -m "feat(skill): submitDraft 写回端点服务层（重名版本化 + 启发式打分闸门）"
```

---

## Task 3: SkillDraftRequest DTO + POST /api/skill/draft 端点

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/dto/SkillDraftRequest.java`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/controller/SkillControllerDraftTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-skill/src/test/java/com/ai/coder/skill/controller/SkillControllerDraftTest.java`:
```java
package com.ai.coder.skill.controller;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SkillController.class)
class SkillControllerDraftTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SkillService skillService;

    @Test
    void submitDraft_returns_ok_delegates_with_user_header() throws Exception {
        Skill drafted = new Skill();
        drafted.setId(1L);
        drafted.setName("code-review");
        drafted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(skillService.submitDraft(eq("code-review"), anyString(), anyString(), eq(7L)))
                .thenReturn(drafted);

        mockMvc.perform(post("/api/skill/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "7")
                        .content("{\"name\":\"code-review\",\"description\":\"审查代码\",\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        verify(skillService).submitDraft(eq("code-review"), eq("审查代码"), eq("x"), eq(7L));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillControllerDraftTest
```
Expected: 404（`/api/skill/draft` 不存在）或编译失败。

- [ ] **Step 3: 实现 SkillDraftRequest DTO**

Create `aicoder-skill/src/main/java/com/ai/coder/skill/dto/SkillDraftRequest.java`:
```java
package com.ai.coder.skill.dto;

import lombok.Data;

/** submit_skill_draft 工具的写回载荷。author 取自请求头 X-User-Id（由 chat 工具透传）。 */
@Data
public class SkillDraftRequest {
    private String name;
    private String description;
    private String content;
}
```

- [ ] **Step 4: SkillController 加 POST /draft**

Modify `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java`：

(a) 加 import：
```java
import com.ai.coder.skill.dto.SkillDraftRequest;
```

(b) 在 `create(...)` 方法后追加：
```java
    @PostMapping("/draft")
    public ResponseEntity<Skill> submitDraft(@RequestBody SkillDraftRequest req,
                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(skillService.submitDraft(
                req.getName(), req.getDescription(), req.getContent(), userId));
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillControllerDraftTest
```
Expected: `Tests run: 1, Failures: 0`。

- [ ] **Step 6: 提交**
```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/dto/SkillDraftRequest.java aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java aicoder-skill/src/test/java/com/ai/coder/skill/controller/SkillControllerDraftTest.java
git commit -m "feat(skill): POST /api/skill/draft 写回端点（chat submit_skill_draft 目标）"
```

---

## Task 4: generate-skill 元技能种子

**Files:**
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/init/SkillDataInitializer.java`
- Test 修改: `aicoder-skill/src/test/java/com/ai/coder/skill/init/SkillDataInitializerTest.java`

- [ ] **Step 1: 更新既有 SkillDataInitializerTest（断言两个种子）**

Modify `aicoder-skill/src/test/java/com/ai/coder/skill/init/SkillDataInitializerTest.java`：

(a) 加 import：
```java
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

(b) 把 `run_seeds_and_materializes_when_directory_writable` 整个替换为：
```java
    @Test
    void run_seeds_both_skills_and_materializes_when_directory_writable() {
        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        initializer = new SkillDataInitializer(repository, new SkillRegistrySyncService(props));

        assertDoesNotThrow(() -> initializer.run());

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(repository, atLeast(2)).save(captor.capture());
        List<Skill> seeded = captor.getAllValues();
        List<String> names = seeded.stream().map(Skill::getName).toList();
        assertTrue(names.contains("greeting-skill"), "应播种 greeting-skill：" + names);
        assertTrue(names.contains("generate-skill"), "应播种 generate-skill 元技能：" + names);
        // 可写目录 → 两个种子都应物化（filePath 非空）
        assertTrue(seeded.stream().allMatch(s -> s.getFilePath() != null),
                "可写目录下所有种子都应物化");
    }
```

(c) 把 `run_does_not_crash_when_directory_unwritable` 中对 `last.getName()` 的断言替换——把：
```java
        Skill seeded = captor.getValue();
        assertEquals("greeting-skill", seeded.getName());
        assertNull(seeded.getFilePath(), "unwritable dir → filePath must stay null (graceful degrade)");
```
改为：
```java
        List<Skill> seeded = captor.getAllValues();
        // 不可写目录 → 所有种子的物化都降级（filePath 为 null），但不崩启动
        assertTrue(seeded.stream().allMatch(s -> s.getFilePath() == null),
                "unwritable dir → 所有种子 filePath 都应为 null（graceful degrade）");
```
并删去不再使用的 `assertEquals`/`assertNull` 静态导入若编译告警（保留也无碍）。

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillDataInitializerTest
```
Expected: `run_seeds_both_skills...` 失败（当前只播种 greeting-skill，names 不含 generate-skill）。

- [ ] **Step 3: 重构 SkillDataInitializer 支持多种子 + 加 generate-skill**

Modify `aicoder-skill/src/main/java/com/ai/coder/skill/init/SkillDataInitializer.java` —— 整体替换为：
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

import java.time.LocalDateTime;

@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class SkillDataInitializer implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final SkillRegistrySyncService syncService;

    @Override
    public void run(String... args) {
        seedIfAbsent("greeting-skill", "问候技能",
                "当用户打招呼或寒暄时使用，以友好的方式回应。",
                """
                ---
                name: greeting-skill
                description: 当用户打招呼或寒暄时使用，以友好的方式回应。
                ---
                # 问候技能
                遇到问候时，先简短回应，再询问可以帮什么忙。保持语气友好、简洁。
                """);
        seedIfAbsent("generate-skill", "生成技能（元技能）",
                "当技能目录中没有任何技能能匹配用户任务、且该任务是可复用的重复性模式时使用——起草一个新的标准化技能并提交审批。",
                """
                ---
                name: generate-skill
                description: 当技能目录中没有任何技能能匹配用户任务、且该任务是可复用的重复性模式时使用——起草一个新的标准化技能并提交审批。
                ---
                # Generate Skill（元技能）

                ## 何时使用
                - 技能目录中无匹配技能，**且**判断该任务以后会重复出现。
                - 一次性问题不要生成技能。

                ## 如何起草
                1. 起一个 kebab-case 的技能名（短、表意，如 `code-review-checklist`）。
                2. 写一句话 description（何时使用）。
                3. 按 SKILL.md 模板写正文：frontmatter(name + description) + 指令步骤 + 必要示例。

                ## 质量标准（低质草稿会被自动拒绝）
                - 名字规范（kebab-case）、描述清晰（10–200 字）、含合法 frontmatter、正文 ≥50 字、指令可执行。

                ## 完成动作
                调用工具：`submit_skill_draft(name, description, content)`
                其中 content 为完整 SKILL.md 文本（含 frontmatter）。
                """);
    }

    private void seedIfAbsent(String name, String displayName, String description, String content) {
        if (skillRepository.existsByName(name)) {
            return;
        }
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDisplayName(displayName);
        skill.setDescription(description);
        skill.setContent(content);
        skill.setVersion(1);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setSource(SkillSource.MANUAL);
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        Skill saved = skillRepository.save(skill);
        try {
            saved.setFilePath(syncService.materialize(saved));
            skillRepository.save(saved);
            log.info("已播种示例技能 {} 并物化到 {}", saved.getName(), saved.getFilePath());
        } catch (Exception e) {
            // 目录不可写/IO 失败时降级：技能已入库但未物化（filePath 留空），不阻断启动。
            log.warn("播种 {}：已写入 DB 但物化失败（检查 skill.directory 权限/路径）：{}", saved.getName(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-skill test -Dtest=SkillDataInitializerTest
```
Expected: `Tests run: 2, Failures: 0`。

- [ ] **Step 5: 跑 skill 模块全量测试回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-skill test
```
Expected: `BUILD SUCCESS`，全部 skill 测试绿。

- [ ] **Step 6: 提交**
```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/init/SkillDataInitializer.java aicoder-skill/src/test/java/com/ai/coder/skill/init/SkillDataInitializerTest.java
git commit -m "feat(skill): 种子 generate-skill 元技能 + 初始化器多种子重构"
```

---

## Task 5: chat 侧 SubmitSkillDraftTool 工具

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/tool/SubmitSkillDraftTool.java`
- Test: `aicoder-chat/src/test/java/com/ai/coder/chat/tool/SubmitSkillDraftToolTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-chat/src/test/java/com/ai/coder/chat/tool/SubmitSkillDraftToolTest.java`:
```java
package com.ai.coder.chat.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmitSkillDraftToolTest {

    private RestTemplate restTemplate;
    private SubmitSkillDraftTool tool;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        tool = new SubmitSkillDraftTool(restTemplate);
    }

    @Test
    void submitSkillDraft_success_returns_status_and_propagates_user_header() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenReturn(Map.of("name", "code-review", "status", "PENDING_APPROVAL"));
        ToolContext ctx = new ToolContext(Map.<String, Object>of("userId", 7L));

        String result = tool.submitSkillDraft("code-review", "审查代码",
                "---\nname: code-review\ndescription: 审查。\n---\n正文足够长以满足最小长度要求。", ctx);

        assertTrue(result.contains("code-review"), result);
        assertTrue(result.contains("PENDING_APPROVAL"), "应在返回里告知状态：" + result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(any(String.class), captor.capture(), eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertTrue(headers.containsKey("X-User-Id"), "应透传 X-User-Id");
        assertEqualsOrContains("7", headers.getFirst("X-User-Id"));
    }

    @Test
    void submitSkillDraft_rest_error_returns_friendly_string_without_throwing() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("skill 服务不可达"));
        ToolContext ctx = new ToolContext(Map.<String, Object>of("userId", 7L));

        String result = assertDoesNotThrow(() ->
                tool.submitSkillDraft("code-review", "审查代码", "正文", ctx));

        assertTrue(result.contains("提交失败"), "异常应降级为友好串：" + result);
    }

    @Test
    void submitSkillDraft_rejects_invalid_name_without_calling_rest() {
        ToolContext ctx = new ToolContext(Map.<String, Object>of("userId", 7L));

        String result = tool.submitSkillDraft("../etc/passwd", "审查代码", "正文", ctx);

        assertTrue(result.contains("非法"), "非法名应本地拒绝：" + result);
        verify(restTemplate, never()).postForObject(any(String.class), any(), any());
    }

    private static void assertEqualsOrContains(String expected, String actual) {
        assertTrue(expected.equals(actual), "X-User-Id 应为 7，实际：" + actual);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-chat test -Dtest=SubmitSkillDraftToolTest
```
Expected: 编译失败（`SubmitSkillDraftTool` 不存在）。

- [ ] **Step 3: 实现 SubmitSkillDraftTool**

Create `aicoder-chat/src/main/java/com/ai/coder/chat/tool/SubmitSkillDraftTool.java`:
```java
package com.ai.coder.chat.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * submit_skill_draft 工具：模型判定无匹配技能、且任务可复用时，起草 SKILL.md 写回 aicoder-skill 审批队列。
 * 用 @LoadBalanced RestTemplate（与 SqlQueryTool 同模式）→ POST http://aicoder-skill/api/skill/draft。
 * userId 经 ToolContext 透传（模型不感知），写入 X-User-Id 头。任何失败降级为友好串，不中断对话轮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitSkillDraftTool {

    private static final String DRAFT_URL = "http://aicoder-skill/api/skill/draft";

    private final RestTemplate restTemplate;

    @Tool(description = "提交一个新技能草稿进入审批队列。当判断当前任务可复用、但技能目录里没有匹配技能时使用。"
            + "参数：name（kebab-case 技能名）、description（一句话描述何时使用）、"
            + "content（完整 SKILL.md 文本，含 frontmatter）。")
    public String submitSkillDraft(String name, String description, String content, ToolContext toolContext) {
        if (name == null || name.isBlank()) {
            return "技能名不能为空，未提交。";
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return "非法的技能名：" + name + "，未提交。";
        }
        Long userId = extractUserId(toolContext);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (userId != null) {
                headers.set("X-User-Id", String.valueOf(userId));
            }
            Map<String, Object> body = Map.of(
                    "name", name,
                    "description", description == null ? "" : description,
                    "content", content == null ? "" : content);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(DRAFT_URL, entity, Map.class);
            String respName = resp == null ? name : String.valueOf(resp.getOrDefault("name", name));
            String status = resp == null ? "未知" : String.valueOf(resp.getOrDefault("status", "未知"));
            log.info("submit_skill_draft 提交：name={} → status={}", respName, status);
            return "技能草稿「" + respName + "」已提交，状态：" + status + "。";
        } catch (Exception e) {
            log.warn("submit_skill_draft 提交失败 name={}：{}", name, e.getMessage());
            return "技能提交失败：" + e.getMessage() + "。请稍后重试。";
        }
    }

    private Long extractUserId(ToolContext toolContext) {
        if (toolContext == null) return null;
        Object v = toolContext.getContext().get("userId");
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-chat test -Dtest=SubmitSkillDraftToolTest
```
Expected: `Tests run: 3, Failures: 0`。

- [ ] **Step 5: 提交**
```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/tool/SubmitSkillDraftTool.java aicoder-chat/src/test/java/com/ai/coder/chat/tool/SubmitSkillDraftToolTest.java
git commit -m "feat(chat): submit_skill_draft 工具（@Tool + ToolContext 透传 userId + RestTemplate）"
```

---

## Task 6: ChatService 挂载 submit_skill_draft 工具 + toolContext

**Files:**
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java`
- Test 修改: `aicoder-chat/src/test/java/com/ai/coder/chat/service/ChatServiceHistoryTest.java`（构造函数加 1 参）

> 说明：本任务是纯装配（注入工具 + 加进 defaultTools + prompt 加 toolContext），无独立单测价值；以「chat 模块全量测试不回归」+ Task 7 端到端作为验证（与 Phase 2 一致）。

- [ ] **Step 1: ChatService 注入工具并装配**

Modify `aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java`：

(a) 加 import：
```java
import com.ai.coder.chat.tool.SubmitSkillDraftTool;
import java.util.Map;
```

(b) 在 `private final ReadSkillTool readSkillTool;` 后加：
```java
    private final SubmitSkillDraftTool submitSkillDraftTool;
```

(c) `buildSkillClient` 的 `.defaultTools(...)` 改为挂两个工具：
```java
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(skillAdvisor)
                .defaultTools(readSkillTool, submitSkillDraftTool);
```

(d) `chat()` 的 prompt 链加 `.toolContext(...)`（在 `.prompt()` 之后、`.system(...)` 之前）：
```java
        String responseContent = buildSkillClient(request.getModel())
                .prompt()
                .toolContext(Map.<String, Object>of("userId", userId))
                .system("你是一个专业的AI编程助手，请用中文回答问题。")
                .messages(historyMessages(conversationId, userMsg.getId()))
                .user(request.getMessage())
                .call()
                .content();
```

(e) `chatStream()` 同样加 `.toolContext(...)`：
```java
        Flux<String> textFlux = buildSkillClient(request.getModel())
                .prompt()
                .toolContext(Map.<String, Object>of("userId", userId))
                .system("你是一个专业的AI编程助手，请用中文回答问题。")
                .messages(historyMessages(conversationId, userMsg.getId()))
                .user(request.getMessage())
                .stream()
                .content();
```

- [ ] **Step 2: 修复既有 ChatServiceHistoryTest 构造函数调用**

Modify `aicoder-chat/src/test/java/com/ai/coder/chat/service/ChatServiceHistoryTest.java` —— 把：
```java
        ChatService service = new ChatService(null, null, repo, null, null);
```
改为：
```java
        ChatService service = new ChatService(null, null, repo, null, null, null);
```
（新增的 `submitSkillDraftTool` 为第 6 个参数，传 null——historyMessages 测试不涉及它。）

- [ ] **Step 3: 编译 + chat 模块全量测试回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-chat test
```
Expected: `BUILD SUCCESS`，chat 模块 11 个测试全绿（Phase 2 既有 8 + Task 5 新增 SubmitSkillDraftToolTest 3；ChatServiceHistoryTest 仍绿）。

- [ ] **Step 4: 提交**
```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java aicoder-chat/src/test/java/com/ai/coder/chat/service/ChatServiceHistoryTest.java
git commit -m "feat(chat): ChatService 挂载 submit_skill_draft + 透传 userId 到 ToolContext"
```

---

## Task 7: 端到端手动验证（自进化闭环）

**Files:** 无代码改动——手动验证清单。

**前置：** MySQL/Redis/Nacos 3.x 运行中；共享 `skill.directory` 已配（Phase 1/2 验证过）；支持 function-calling 的模型可用（如 DeepSeek）。

- [ ] **Step 1: 启动服务（按依赖顺序）**

分别在独立终端启动：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-gateway
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-skill
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-chat
```
确认：aicoder-skill 启动日志含「已播种示例技能 generate-skill 并物化到 ...」；chat 启动日志 `[skill] directory` 指向共享目录。

- [ ] **Step 2: 验证元技能进目录**

向 chat 提问（经 gateway :8080，带有效 JWT）：
> 你有哪些技能？
预期回复列出 `greeting-skill` 与 `generate-skill`（advisor 注入的目录）。日志确认 advisor reload 扫到 2 个技能。

- [ ] **Step 3: 触发自主生成（写侧闭环）**

提问一个目录里没有、但可复用的任务，例如：
> 每次我都需要你帮我给代码改动写规范的 commit message，以后也能用。
预期：模型 read_skill("generate-skill") → 起草 → 调 submit_skill_draft。chat 日志含 `submit_skill_draft 提交：name=...`。

- [ ] **Step 4: 验证草稿进 skill 审批队列**

查 skill 服务：
```bash
curl -s http://localhost:8080/api/skill/pending | head
```
预期：返回刚提交的草稿（status=PENDING_APPROVAL，source=AUTO_GENERATED，quality_score≥60）。若打分 < 阈值则在 `/api/skill` 列表里 status=REJECTED（rationione 见打分器）。

- [ ] **Step 5: 审批 + 物化 + 闭环复用**

```bash
# 假设草稿 id=10
curl -s -X PUT http://localhost:8080/api/skill/10/approve -H "X-User-Id: 1"
```
预期：返回 status=ACTIVE、filePath 指向共享目录下的新 SKILL.md。

再向 chat 提一次类似任务，预期：新技能已进 advisor 目录（每请求 reload 自动可见），模型直接 read_skill 复用，**不再重新生成** → 闭环完成。

- [ ] **Step 6: 记录验证结果**

把端到端结论（哪些步骤通过/异常）写进提交说明或团队记录。无需提交代码。

---

## 完成后

使用 superpowers:finishing-a-development-branch 收尾（验证全部测试 → 呈现选项 → 按用户选择执行）。本轮所有提交默认留本地 main、不推送（沿用 Phase 2 姿势）。
