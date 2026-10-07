# Agent Skill Phase 4：技能管理前端 + 意图生成后端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给自进化循环补上人类可用的管理面（aicoder-web 技能管理页：列表/过滤/查看渲染/手动 CRUD/审批/归档/意图生成）+ 服务端意图生成后端（`SkillGenerationService`）。

**Architecture:** skill 模块加 `spring-ai-starter-model-deepseek` + `SkillGenerationService`（按 generate-skill 元技能模板 + 意图调 DeepSeek → 解析 frontmatter → 复用 `submitDraft` 打分闸门 sink）；前端照 `ModelConfigView` 模式新建 `SkillManageView.vue`（状态过滤 tabs + 表格 + 详情 modal marked 渲染 + 新建/编辑 modal + 生成 modal），加 `api/skill.ts` + `SkillDTO` 类型 + `marked` 依赖 + `/skill` 路由。

**Tech Stack:** 后端 Spring Boot 3.5 + Spring AI 1.1.2（DeepSeek starter）；前端 Vue 3 `<script setup>` + vue-router(hash) + axios + marked；Maven（`/Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`，`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`）。

**Spec:** [2026-06-16-skill-mgmt-frontend-and-generation-design.md](../specs/2026-06-16-skill-mgmt-frontend-and-generation-design.md)

**Build/test 命令：**
- 后端：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-skill test`
- 前端：`cd aicoder-web && npm run build`（vue-tsc 类型检查 + vite 构建；前端无单测基建）
- 前端 dev：`cd aicoder-web && npm run dev`（:8888，代理 /api→:8080 Gateway）

---

## File Structure

**后端 `aicoder-skill`（`com.ai.coder.skill`）**
- Create `dto/GenerateSkillRequest.java` — `{intent}`
- Create `service/SkillGenerationService.java` — 注入 ChatModel + SkillService + SkillRepository；按模板+意图生成→解析→submitDraft
- Modify `controller/SkillController.java` — 加 `POST /api/skill/generate`
- Modify `pom.xml` — 加 `spring-ai-starter-model-deepseek`
- Modify `application.yml` — `spring.ai.deepseek.api-key` + `skill.generate-model`
- Test: `service/SkillGenerationServiceTest.java`（新）、`controller/SkillControllerGenerateTest.java`（新）

**前端 `aicoder-web`**
- Create `api/skill.ts`
- Modify `types/index.ts` — 加 Skill 相关类型
- Create `views/skill/SkillManageView.vue`
- Modify `router/index.ts` — 加 `/skill` 路由
- Modify `package.json` — 加 `marked`

**本轮延后（设计注明）**：侧边栏菜单项（DB 驱动 + 跨模块 RBAC 播种复杂；页面经 `/#/skill` 可达，菜单可事后用既有「菜单管理」UI 加）。

---

## Task 1: skill 模块加 DeepSeek 依赖 + 配置

**Files:**
- Modify: `aicoder-skill/pom.xml`
- Modify: `aicoder-skill/src/main/resources/application.yml`

> 纯基础设施，无测试；以「模块编译 + 能注入 ChatModel bean」为验证（Task 2 的测试会真正用到）。

- [ ] **Step 1: pom 加 deepseek starter**

在 `aicoder-skill/pom.xml` 的 `<dependencies>` 内（紧邻其它 spring-cloud 依赖后）加：
```xml
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-deepseek</artifactId>
        </dependency>
```
（版本由父 pom 的 `spring-ai-bom` 1.1.2 管理，免 `<version>`。）

- [ ] **Step 2: application.yml 加 deepseek 配置**

在 `aicoder-skill/src/main/resources/application.yml` 加（与 chat 同一 key；`spring:` 顶层下）：
```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-v4-flash
```
并在文件末尾的 `skill:` 节加生成模型配置（如已有 `skill:` 节则并入）：
```yaml
skill:
  generate-model: deepseek-v4-flash
```
（若 application.yml 已有顶层 `spring:` 块，把 `ai:` 并入而非重复声明。）

- [ ] **Step 3: 验证编译**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill compile
```
Expected: BUILD SUCCESS（starter 拉取后 deepseek 类可用）。

- [ ] **Step 4: 提交**
```bash
git add aicoder-skill/pom.xml aicoder-skill/src/main/resources/application.yml
git commit -m "feat(skill): 加 spring-ai-starter-model-deepseek + 生成模型配置"
```

---

## Task 2: SkillGenerationService（TDD）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillGenerationService.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillGenerationServiceTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillGenerationServiceTest.java`:
```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGenerationServiceTest {

    private ChatModel chatModel;
    private SkillService skillService;
    private SkillRepository skillRepository;
    private SkillGenerationService service;

    private static final String META_CONTENT = """
            ---
            name: generate-skill
            description: 元技能
            ---
            # Generate Skill
            指令...
            """;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        skillService = mock(SkillService.class);
        skillRepository = mock(SkillRepository.class);
        Skill meta = new Skill();
        meta.setName("generate-skill");
        meta.setContent(META_CONTENT);
        when(skillRepository.findByName("generate-skill")).thenReturn(Optional.of(meta));
        service = new SkillGenerationService(chatModel, skillService, skillRepository);
    }

    @Test
    void generate_parses_frontmatter_and_submits_draft() {
        String modelOutput = """
                ---
                name: code-review-checklist
                description: 对代码变更做清单式审查。
                ---
                # Code Review Checklist
                审查可读性、命名、错误处理、测试覆盖、性能与安全，给出修改建议与示例。
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(resp(modelOutput));
        Skill submitted = new Skill();
        submitted.setName("code-review-checklist");
        submitted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(skillService.submitDraft(any(), any(), any(), any())).thenReturn(submitted);

        Skill result = service.generate("帮我审查代码", 7L);

        verify(skillService).submitDraft(
                org.mockito.ArgumentMatchers.eq("code-review-checklist"),
                org.mockito.ArgumentMatchers.eq("对代码变更做清单式审查。"),
                org.mockito.ArgumentMatchers.contains("# Code Review Checklist"),
                org.mockito.ArgumentMatchers.eq(7L));
        assertEquals("code-review-checklist", result.getName());
    }

    @Test
    void generate_falls_back_when_no_frontmatter() {
        // 模型输出无 frontmatter → name 降级为 intent 的 slug，仍调 submitDraft（交打分器判 REJECTED）
        when(chatModel.call(any(Prompt.class))).thenReturn(resp("一段没有 frontmatter 的杂乱文本"));
        Skill submitted = new Skill();
        submitted.setName("bangwo-shencha-daima");
        submitted.setStatus(SkillStatus.REJECTED);
        when(skillService.submitDraft(any(), any(), any(), any())).thenReturn(submitted);

        service.generate("帮我审查代码", 7L);

        verify(skillService).submitDraft(
                org.mockito.ArgumentMatchers.eq("bangwo-shencha-daima"),
                any(), any(), org.mockito.ArgumentMatchers.eq(7L));
    }

    private static ChatResponse resp(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillGenerationServiceTest
```
Expected: 编译失败（`SkillGenerationService` 不存在）。

- [ ] **Step 3: 实现 SkillGenerationService**

Create `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillGenerationService.java`:
```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图→技能草稿生成：按 generate-skill 元技能模板 + 用户意图调 DeepSeek → 解析 frontmatter →
 * 经 SkillService.submitDraft（打分+闸门+版本化）入库。与 chat 自主生成同 sink，行为一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGenerationService {

    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$");
    private static final Pattern NAME_LINE = Pattern.compile("(?m)^name:\\s*(.+?)\\s*$");
    private static final Pattern DESC_LINE = Pattern.compile("(?m)^description:\\s*(.+?)\\s*$");

    private final ChatModel chatModel;
    private final SkillService skillService;
    private final SkillRepository skillRepository;

    public Skill generate(String intent, Long userId) {
        String instruction = skillRepository.findByName("generate-skill")
                .map(Skill::getContent)
                .orElse("起草一个标准化 SKILL.md（含 frontmatter: name + description）。");

        String output = ChatClient.builder(chatModel).build()
                .prompt()
                .system(instruction)
                .user("## 技能意图\n" + intent + "\n\n请严格按上面的指引起草一个 SKILL.md 并完整输出（含 frontmatter: name + description）。")
                .call()
                .content();

        Parsed parsed = parse(output, intent);
        log.info("生成技能草稿：intent='{}' → name='{}'", intent, parsed.name);
        return skillService.submitDraft(parsed.name, parsed.description, output, userId);
    }

    private Parsed parse(String output, String intent) {
        Matcher fm = FRONTMATTER.matcher(output == null ? "" : output.stripLeading());
        if (fm.find()) {
            String block = fm.group(1);
            String name = first(NAME_LINE, block);
            String desc = first(DESC_LINE, block);
            if (name != null) {
                return new Parsed(name, desc != null ? desc : intent);
            }
        }
        // 降级：无合法 frontmatter → intent 转 slug 作 name，description 用 intent（交打分器判 REJECTED）
        return new Parsed(slug(intent), intent);
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String slug(String intent) {
        String s = intent == null ? "skill" : intent.trim().toLowerCase();
        String ascii = s.replaceAll("[^a-z0-9\\s-]", "");
        String slug = ascii.trim().split("\\s+").length > 0
                ? String.join("-", ascii.trim().split("\\s+")) : "skill";
        return slug.isBlank() ? "skill" : slug;
    }

    private record Parsed(String name, String description) {}
}
```

- [ ] **Step 4: 运行测试确认通过**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-skill test -Dtest=SkillGenerationServiceTest
```
Expected: `Tests run: 2, Failures: 0`。

- [ ] **Step 5: 提交**
```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillGenerationService.java aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillGenerationServiceTest.java
git commit -m "feat(skill): SkillGenerationService 意图→草稿生成（复用 submitDraft sink）"
```

---

## Task 3: GenerateSkillRequest DTO + POST /api/skill/generate（TDD）

**Files:**
- Create: `aicoder-skill/src/main/java/com/ai/coder/skill/dto/GenerateSkillRequest.java`
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java`
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/controller/SkillControllerGenerateTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-skill/src/test/java/com/ai/coder/skill/controller/SkillControllerGenerateTest.java`:
```java
package com.ai.coder.skill.controller;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.service.SkillGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SkillController.class)
class SkillControllerGenerateTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SkillGenerationService generationService;

    // SkillController 还依赖 SkillService；@WebMvcTest 需要把它也 mock 掉
    @MockBean
    com.ai.coder.skill.service.SkillService skillService;

    @Test
    void generate_returns_ok_and_delegates() throws Exception {
        Skill drafted = new Skill();
        drafted.setId(9L);
        drafted.setName("code-review-checklist");
        drafted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(generationService.generate(eq("帮我审查代码"), eq(7L))).thenReturn(drafted);

        mockMvc.perform(post("/api/skill/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "7")
                        .content("{\"intent\":\"帮我审查代码\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review-checklist"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        verify(generationService).generate(eq("帮我审查代码"), eq(7L));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-skill test -Dtest=SkillControllerGenerateTest
```
Expected: 404（`/api/skill/generate` 不存在）或编译失败。

- [ ] **Step 3: 实现 GenerateSkillRequest DTO**

Create `aicoder-skill/src/main/java/com/ai/coder/skill/dto/GenerateSkillRequest.java`:
```java
package com.ai.coder.skill.dto;

import lombok.Data;

/** 「生成技能」入口载荷：用户描述的技能意图。author 取自 X-User-Id 头。 */
@Data
public class GenerateSkillRequest {
    private String intent;
}
```

- [ ] **Step 4: SkillController 加 POST /generate + 注入 SkillGenerationService**

在 `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java`：

(a) 加 import：
```java
import com.ai.coder.skill.dto.GenerateSkillRequest;
import com.ai.coder.skill.service.SkillGenerationService;
```

(b) 加字段（与现有 `private final SkillService skillService;` 并列）：
```java
    private final SkillGenerationService generationService;
```

(c) 加端点（在 `submitDraft`/`/draft` 之后）：
```java
    @PostMapping("/generate")
    public ResponseEntity<Skill> generate(@RequestBody GenerateSkillRequest req,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(generationService.generate(req.getIntent(), userId));
    }
```

- [ ] **Step 5: 运行测试确认通过 + skill 模块回归**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-skill test
```
Expected: BUILD SUCCESS，全部 skill 测试绿（含新的 Generate 测试）。

- [ ] **Step 6: 提交**
```bash
git add aicoder-skill/src/main/java/com/ai/coder/skill/dto/GenerateSkillRequest.java aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java aicoder-skill/src/test/java/com/ai/coder/skill/controller/SkillControllerGenerateTest.java
git commit -m "feat(skill): POST /api/skill/generate 意图生成端点"
```

---

## Task 4: 前端 api/skill.ts + 类型 + marked 依赖

**Files:**
- Create: `aicoder-web/src/api/skill.ts`
- Modify: `aicoder-web/src/types/index.ts`
- Modify: `aicoder-web/package.json`（`npm install marked`）

> 前端无单测基建；以 `npm run build`（vue-tsc 类型检查）为验证。

- [ ] **Step 1: 加 marked 依赖**
```bash
cd aicoder-web && npm install marked
```
（marked v5+ 自带 TS 类型，无需 @types/marked。）

- [ ] **Step 2: types/index.ts 加 Skill 类型**

在 `aicoder-web/src/types/index.ts` 末尾追加：
```typescript
export interface SkillDTO {
  id: number
  name: string
  displayName: string | null
  description: string
  content: string
  version: number
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'ARCHIVED'
  source: 'MANUAL' | 'AUTO_GENERATED'
  category: string | null
  tags: string | null
  qualityScore: number | null
  trialResult: string | null
  filePath: string | null
  parentSkillId: number | null
  authorUserId: number | null
  approvedBy: number | null
  approvedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateSkillRequest {
  name: string
  displayName?: string
  description: string
  content: string
  category?: string
  tags?: string
  source?: 'MANUAL' | 'AUTO_GENERATED'
}

export interface GenerateSkillRequest {
  intent: string
}
```

- [ ] **Step 3: 实现 api/skill.ts**

Create `aicoder-web/src/api/skill.ts`:
```typescript
import request from './request'
import type { SkillDTO, CreateSkillRequest, GenerateSkillRequest } from '@/types'

export const skillApi = {
  list: () => request.get<SkillDTO[]>('/skill'),
  pending: () => request.get<SkillDTO[]>('/skill/pending'),
  getById: (id: number) => request.get<SkillDTO>(`/skill/${id}`),
  create: (data: CreateSkillRequest) => request.post<SkillDTO>('/skill', data),
  update: (id: number, data: CreateSkillRequest) => request.put<SkillDTO>(`/skill/${id}`, data),
  submit: (id: number) => request.put<SkillDTO>(`/skill/${id}/submit`),
  approve: (id: number) => request.put<SkillDTO>(`/skill/${id}/approve`),
  reject: (id: number) => request.put<SkillDTO>(`/skill/${id}/reject`),
  archive: (id: number) => request.put<SkillDTO>(`/skill/${id}/archive`),
  // LLM 调用耗时较长，单独放宽超时到 120s（request.ts 默认 30s）
  generate: (data: GenerateSkillRequest) =>
    request.post<SkillDTO>('/skill/generate', data, { timeout: 120000 })
}
```

- [ ] **Step 4: 类型检查**
```bash
cd aicoder-web && npx vue-tsc --noEmit
```
Expected: 无新增类型错误（既有错误若有，不增不减）。

- [ ] **Step 5: 提交**
```bash
git add aicoder-web/src/api/skill.ts aicoder-web/src/types/index.ts aicoder-web/package.json aicoder-web/package-lock.json
git commit -m "feat(web): skill api 客户端 + SkillDTO 类型 + marked 依赖"
```

---

## Task 5: SkillManageView.vue 技能管理页 + 路由

**Files:**
- Create: `aicoder-web/src/views/skill/SkillManageView.vue`
- Modify: `aicoder-web/src/router/index.ts`

> 照 `views/model/ModelConfigView.vue` 模式（page-header + card 表格 + tag + modal-overlay）；加状态过滤 tabs、详情 modal（marked 渲染）、生成 modal。验证：`npm run build` + 手动 e2e（Task 6）。

- [ ] **Step 1: 实现 SkillManageView.vue**

Create `aicoder-web/src/views/skill/SkillManageView.vue`:
```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { marked } from 'marked'
import { skillApi } from '@/api/skill'
import type { SkillDTO, CreateSkillRequest } from '@/types'

const allSkills = ref<SkillDTO[]>([])
const loading = ref(false)
const statusFilter = ref<string>('ALL')
const statuses = ['ALL', 'PENDING_APPROVAL', 'DRAFT', 'ACTIVE', 'REJECTED', 'ARCHIVED']

const skills = computed(() =>
  statusFilter.value === 'ALL'
    ? allSkills.value
    : allSkills.value.filter(s => s.status === statusFilter.value)
)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await skillApi.list()
    allSkills.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}
onMounted(fetchList)

// 详情 modal
const detailSkill = ref<SkillDTO | null>(null)
const detailHtml = computed(() =>
  detailSkill.value ? marked.parse(detailSkill.value.content) as string : '')
const openDetail = (s: SkillDTO) => { detailSkill.value = s }

// 新建/编辑 modal
const showForm = ref(false)
const editingId = ref<number | null>(null)
const defaultForm: CreateSkillRequest = { name: '', displayName: '', description: '', content: '', source: 'MANUAL' }
const form = ref<CreateSkillRequest>({ ...defaultForm })
const openCreate = () => { editingId.value = null; form.value = { ...defaultForm }; showForm.value = true }
const openEdit = (s: SkillDTO) => {
  editingId.value = s.id
  form.value = { name: s.name, displayName: s.displayName || '', description: s.description, content: s.content, category: s.category || '', tags: s.tags || '', source: s.source }
  showForm.value = true
}
const handleSave = async () => {
  try {
    if (editingId.value) await skillApi.update(editingId.value, form.value)
    else await skillApi.create(form.value)
    showForm.value = false
    await fetchList()
  } catch { /* ignore */ }
}

// 生成 modal
const showGenerate = ref(false)
const intent = ref('')
const generating = ref(false)
const openGenerate = () => { intent.value = ''; showGenerate.value = true }
const handleGenerate = async () => {
  if (!intent.value.trim()) return
  generating.value = true
  try {
    await skillApi.generate({ intent: intent.value })
    showGenerate.value = false
    await fetchList()
  } catch { /* ignore */ } finally {
    generating.value = false
  }
}

// 审批/归档动作
const handleAction = async (s: SkillDTO, action: 'submit' | 'approve' | 'reject' | 'archive') => {
  const labels: Record<string, string> = { submit: '提审', approve: '通过', reject: '拒绝', archive: '归档' }
  if (!confirm(`确定${labels[action]}技能「${s.name}」？`)) return
  try {
    await skillApi[action](s.id)
    await fetchList()
  } catch { /* ignore */ }
}

const statusTagClass = (s: string) => ({
  ACTIVE: 'tag-green', PENDING_APPROVAL: 'tag-orange', DRAFT: 'tag-gray',
  REJECTED: 'tag-red', ARCHIVED: 'tag-gray'
}[s] || 'tag-gray')
const statusLabel = (s: string) => ({
  ACTIVE: '已生效', PENDING_APPROVAL: '待审批', DRAFT: '草稿', REJECTED: '已拒绝', ARCHIVED: '已归档'
}[s] || s)
</script>

<template>
  <div class="skill-mgmt-view">
    <div class="page-header">
      <h2>技能管理</h2>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="openGenerate">✨ 生成技能</button>
        <button class="btn btn-primary" @click="openCreate">+ 新建技能</button>
      </div>
    </div>

    <div class="filter-tabs">
      <button v-for="s in statuses" :key="s" class="filter-tab" :class="{ active: statusFilter === s }" @click="statusFilter = s">
        {{ s === 'ALL' ? '全部' : statusLabel(s) }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>技能名</th><th>描述</th><th>状态</th><th>来源</th><th>质量分</th><th>版本</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in skills" :key="s.id">
            <td><span class="tag tag-blue">{{ s.name }}</span></td>
            <td class="desc-cell">{{ s.description }}</td>
            <td><span class="tag" :class="statusTagClass(s.status)">{{ statusLabel(s.status) }}</span></td>
            <td><span class="tag" :class="s.source === 'AUTO_GENERATED' ? 'tag-purple' : 'tag-gray'">{{ s.source === 'AUTO_GENERATED' ? '自动' : '手动' }}</span></td>
            <td>{{ s.qualityScore ?? '-' }}</td>
            <td>v{{ s.version }}</td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openDetail(s)">查看</button>
                <button class="btn btn-secondary btn-sm" @click="openEdit(s)">编辑</button>
                <button v-if="s.status === 'DRAFT'" class="btn btn-primary btn-sm" @click="handleAction(s, 'submit')">提审</button>
                <button v-if="s.status === 'PENDING_APPROVAL'" class="btn btn-primary btn-sm" @click="handleAction(s, 'approve')">通过</button>
                <button v-if="s.status === 'PENDING_APPROVAL'" class="btn btn-danger btn-sm" @click="handleAction(s, 'reject')">拒绝</button>
                <button v-if="s.status === 'ACTIVE'" class="btn btn-danger btn-sm" @click="handleAction(s, 'archive')">归档</button>
              </div>
            </td>
          </tr>
          <tr v-if="skills.length === 0"><td colspan="7" class="empty-row">暂无技能</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 详情 modal（marked 渲染） -->
    <div v-if="detailSkill" class="modal-overlay" @click.self="detailSkill = null">
      <div class="modal-content card detail-modal">
        <h3>{{ detailSkill.name }} <span class="tag" :class="statusTagClass(detailSkill.status)">{{ statusLabel(detailSkill.status) }}</span></h3>
        <div class="detail-meta">
          <span>来源：{{ detailSkill.source === 'AUTO_GENERATED' ? '自动生成' : '手动' }}</span>
          <span>质量分：{{ detailSkill.qualityScore ?? '-' }}</span>
          <span>版本：v{{ detailSkill.version }}</span>
        </div>
        <div class="md-preview" v-html="detailHtml"></div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="detailSkill = null">关闭</button>
        </div>
      </div>
    </div>

    <!-- 新建/编辑 modal -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑技能' : '新建技能' }}</h3>
        <div class="form-group"><label>技能名 (kebab-case) <span class="required">*</span></label><input v-model="form.name" placeholder="如 code-review-checklist" /></div>
        <div class="form-group"><label>展示名</label><input v-model="form.displayName" placeholder="可选" /></div>
        <div class="form-group"><label>描述 <span class="required">*</span></label><input v-model="form.description" placeholder="一句话：何时使用" /></div>
        <div class="form-group"><label>SKILL.md 内容 <span class="required">*</span></label><textarea v-model="form.content" rows="10" placeholder="---&#10;name: xxx&#10;description: xxx&#10;---&#10;# 技能正文"></textarea></div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showForm = false">取消</button>
          <button class="btn btn-primary" @click="handleSave">保存</button>
        </div>
      </div>
    </div>

    <!-- 生成 modal -->
    <div v-if="showGenerate" class="modal-overlay" @click.self="!generating && (showGenerate = false)">
      <div class="modal-content card">
        <h3>✨ 生成技能</h3>
        <div class="form-group"><label>描述你想要的技能意图 <span class="required">*</span></label><textarea v-model="intent" rows="5" placeholder="如：每次帮我给代码改动写约定式 commit message"></textarea></div>
        <div class="hint">将调用 LLM 按 generate-skill 元技能模板起草，自动打分后进入审批队列。</div>
        <div class="modal-actions">
          <button class="btn btn-secondary" :disabled="generating" @click="showGenerate = false">取消</button>
          <button class="btn btn-primary" :disabled="generating || !intent.trim()" @click="handleGenerate">{{ generating ? '生成中...' : '生成' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.skill-mgmt-view { height: 100%; }
.page-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
  h2 { font-family: 'Sora', sans-serif; font-size: 18px; font-weight: 600; color: var(--text-primary); }
  .header-actions { display: flex; gap: 8px; }
}
.filter-tabs { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-tab {
  padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border-default);
  background: transparent; color: var(--text-secondary); font-size: 13px; cursor: pointer;
  &.active { background: var(--accent); color: #fff; border-color: var(--accent); }
}
.loading { text-align: center; padding: 60px; color: var(--text-muted); }
.table-container { overflow-x: auto; padding: 0;
  table { width: 100%; border-collapse: collapse;
    th, td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--border-default); font-size: 14px; }
    th { background: var(--bg-overlay); font-weight: 600; color: var(--text-secondary); font-family: 'Sora', sans-serif; font-size: 12px; }
    td { color: var(--text-primary); }
  }
}
.desc-cell { max-width: 320px; color: var(--text-secondary); }
.empty-row { text-align: center !important; color: var(--text-muted) !important; padding: 40px !important; }
.action-btns { display: flex; gap: 6px; flex-wrap: wrap; }
.detail-modal { max-width: 760px; width: 90%; }
.detail-meta { display: flex; gap: 16px; color: var(--text-muted); font-size: 12px; margin-bottom: 12px; }
.md-preview {
  max-height: 50vh; overflow: auto; padding: 12px; border: 1px solid var(--border-default); border-radius: 6px;
  background: var(--bg-overlay); color: var(--text-primary); line-height: 1.6;
  :deep(h1), :deep(h2), :deep(h3) { font-family: 'Sora', sans-serif; margin: 0.6em 0 0.3em; }
  :deep(code) { background: rgba(127,127,127,0.2); padding: 2px 5px; border-radius: 3px; font-size: 0.9em; }
  :deep(pre) { background: rgba(0,0,0,0.3); padding: 10px; border-radius: 6px; overflow-x: auto; }
  :deep(table) { border-collapse: collapse; margin: 8px 0; th, td { border: 1px solid var(--border-default); padding: 6px 10px; } }
}
.hint { color: var(--text-muted); font-size: 12px; margin-top: -6px; }
.required { color: #e74c3c; }
</style>
```

- [ ] **Step 2: router 加路由**

在 `aicoder-web/src/router/index.ts` 的 MainLayout `children` 数组里（`model/vectordb` 之后、`profile` 之前）加：
```typescript
      { path: 'skill', name: 'Skill', component: () => import('@/views/skill/SkillManageView.vue') },
```

- [ ] **Step 3: 构建验证（vue-tsc + vite）**
```bash
cd aicoder-web && npm run build
```
Expected: 构建成功，无 TS 错误。

- [ ] **Step 4: 提交**
```bash
git add aicoder-web/src/views/skill/SkillManageView.vue aicoder-web/src/router/index.ts
git commit -m "feat(web): SkillManageView 技能管理页（列表/过滤/审批/生成）+ /skill 路由"
```

---

## Task 6: 端到端手动验证

**Files:** 无代码改动——手动验证清单。

**前置：** MySQL/Redis/Nacos 起中；支持 function-calling 的 DeepSeek 模型已配；`npm install` 已跑（marked 装好）。

- [ ] **Step 1: 启动后端服务**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-gateway > /tmp/gw.log 2>&1 &
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-system > /tmp/sys.log 2>&1 &
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-admin > /tmp/admin.log 2>&1 &
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-skill > /tmp/skill-svc.log 2>&1 &
```
确认 skill 服务启动日志含「注册 Chat 模型」无报错（deepseek starter 生效）。

- [ ] **Step 2: 启动前端 + 登录**

```bash
cd aicoder-web && npm run dev
```
浏览器开 `http://localhost:8888`，用 admin 登录。

- [ ] **Step 3: 验证列表 + marked 预览**

地址栏访问 `http://localhost:8888/#/skill`。预期：列表显示 greeting-skill / generate-skill / commit-message-skill（Phase 3 产物）。点「查看」→ 详情 modal 内 SKILL.md **marked 渲染**（标题/代码块/表格）。

- [ ] **Step 4: 验证「生成技能」入口**

点「✨ 生成技能」→ 输入意图（如"代码审查清单"）→ 生成。预期：生成完成后列表刷新，出现新草稿（source=自动、status=PENDING_APPROVAL 或 REJECTED、qualityScore 已打分）。skill 服务日志含「生成技能草稿：intent='...' → name='...'」。

- [ ] **Step 5: 验证审批闭环**

对新草稿点「通过」→ status 变 ACTIVE。下一轮 chat（Phase 3 已验证的 chat 服务，若未起则起 aicoder-chat）应能在目录看到该技能（advisor reload）。可选：归档一个 ACTIVE 技能 → 确认从 chat 目录消失。

- [ ] **Step 6: 记录结论**

把 e2e 结论（通过/异常）记录。无需提交代码。停服务清理。

---

## 完成后

使用 superpowers:finishing-a-development-branch 收尾（后端跑 `mvn -pl aicoder-skill test` 绿、前端跑 `npm run build` 绿 → 呈现选项 → 按用户选择执行）。本轮提交默认留本地 main、不推送（沿用既有姿势）。
