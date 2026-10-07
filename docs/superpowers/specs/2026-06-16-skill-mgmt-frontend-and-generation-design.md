# Agent Skill 体系 — Phase 4：技能管理前端 + 意图生成后端设计

> **关联：** 系统设计 [2026-06-13-agent-skill-system-design.md](2026-06-13-agent-skill-system-design.md)（§9 前端）、Phase 3 写侧 [2026-06-16-agent-skill-phase3-write-loop-design.md](2026-06-16-agent-skill-phase3-write-loop-design.md)。

## 1. 背景

Phase 1-3 已完成后端闭环：技能 CRUD/状态机/审批/物化（管理面）、chat 读侧消费技能、chat 写侧自主生成草稿并经启发式打分闸门进审批队列。但**审批与人工管理目前只能 curl**，且 Phase 3 延后的「服务端 LLM 从意图生成草稿」（`SkillGenerationService`）尚未建。本阶段补齐这两块，让自进化循环对人类可用。

## 2. 本轮范围（已与用户确认）

- **完整技能管理页**（aicoder-web）：全状态列表 + 状态过滤 + 查看（SKILL.md **markdown 渲染预览**）+ 手动新建/编辑 + 审批（通过/拒绝）+ 归档 + 「生成技能」入口。
- **意图生成后端**（`SkillGenerationService`，Phase 3 延后项）：`POST /api/skill/generate` 接受意图描述 → skill 服务调 DeepSeek 按 `generate-skill` 元技能模板起草 SKILL.md → 经既有 `submitDraft`（打分+闸门+版本化）入库。
- **markdown 预览**：加 `marked` 库渲染 SKILL.md。

**本轮不做（YAGNI）**：chat/rag/workflow 前端技能化；LLM-as-judge 打分；版本血缘可视化；技能调用统计；Ollama 生成（质量/格式不稳定，仅 DeepSeek）。

## 3. 调研结论

| 事实 | 来源/校验 |
|---|---|
| 前端无 UI 组件库——纯 Vue 3 `<script setup>` + vue-router(hash) + pinia + axios；每特性 `api/<x>.ts`（`request.get/put<Resp>('/<path>')`，request.ts 注 JWT + Vite 代理 `/api`→Gateway）+ `views/<x>/*View.vue`（page-header + card 表格 + tag + modal-overlay 表单 + scoped scss，复用 `card/btn-*/tag-*` 类与 `--text-*`/`--border-*` CSS 变量） | aicoder-web 代码勘察（`views/model/ModelConfigView.vue`、`api/model.ts`） |
| 路由扁平挂在 `MainLayout` 子项；菜单在 `layouts/MainLayout.vue` | `router/index.ts` |
| skill 模块**无** spring-ai/deepseek 依赖（纯 CRUD：web+jpa+redis+nacos）；chat 用 `spring-ai-starter-model-deepseek` | skill/chat pom 反查 |
| 后端 `/api/skill/**` 端点 Phase 1/3 全就绪（list/listPending/getById/create/update/submit/approve/reject/archive/draft）；Gateway 已路由 `/api/skill/**`→`lb://aicoder-skill` | Phase 1/3 + gateway application.yml |
| 既有前端无测试基建（无 vitest/jest）——验证靠手动 e2e | package.json |

## 4. 架构与数据流

```
aicoder-web 技能管理页（/skill，JWT 经 request.ts 自动附加）
  ├─ 列表 + 状态过滤 tabs → GET /api/skill (+/pending)
  ├─ 查看详情 → GET /api/skill/{id} → modal 内 marked 渲染 content
  ├─ 手动新建/编辑 → POST/PUT /api/skill [+ PUT /{id}/submit 提审]
  ├─ 审批（PENDING）→ PUT /api/skill/{id}/approve | /reject
  ├─ 归档（ACTIVE）→ PUT /api/skill/{id}/archive
  └─ 生成技能（输入意图）→ POST /api/skill/generate {intent}
        ↓ Vite 代理 /api → Gateway（JWT 校验）→ /api/skill/**
skill 服务 SkillGenerationService.generate(intent, userId):
  注入 DeepSeekChatModel（新增 starter 自动配置）
  system = generate-skill 元技能 SKILL.md 指令
  user   = "技能意图：{intent}\n请严格按指令输出一个 SKILL.md（含 frontmatter）"
  → 模型输出 SKILL.md 文本
  → 解析 frontmatter 取 name/description；content = 完整输出
  → skillService.submitDraft(name, description, content, userId)
    （复用打分+闸门+版本化，与 chat 自主生成同 sink）
  → 返回 Skill（PENDING_APPROVAL / REJECTED）
```

## 5. 组件清单

### 后端（`aicoder-skill`）
| 组件 | 改动 | 职责 |
|---|---|---|
| `pom.xml` | 改动 | 加 `spring-ai-starter-model-deepseek`（同 chat） |
| `application.yml`（或 Nacos 共享） | 改动 | `spring.ai.deepseek.api-key`（同 chat key）+ `skill.generate-model: deepseek-v4-flash`（可配） |
| `dto/GenerateSkillRequest` | 新增 | `{intent: String}` |
| `service/SkillGenerationService` | 新增 | 注入 `ChatModel`；按 generate-skill 模板 + intent 调模型；解析 frontmatter；调 `submitDraft`。纯编排，模型可 mock 便于单测 |
| `controller/SkillController` | 改动 | 加 `POST /api/skill/generate`（@RequestBody GenerateSkillRequest + `X-User-Id` 头）→ `SkillGenerationService.generate` |

### 前端（`aicoder-web`）
| 组件 | 改动 | 职责 |
|---|---|---|
| `api/skill.ts` | 新增 | list / listPending / getById / create / update / submit / approve / reject / archive / generate（`request.get/post/put/delete`） |
| `types/index.ts` | 改动 | 加 `SkillDTO`（镜像 Skill 实体：id/name/displayName/description/content/version/status/source/category/tags/qualityScore/trialResult/filePath/parentSkillId/authorUserId/approvedBy/approvedAt/createdAt/updatedAt）+ `CreateSkillRequest`/`GenerateSkillRequest` |
| `views/skill/SkillManageView.vue` | 新增 | page-header（标题 +「新建技能」「生成技能」按钮）→ 状态过滤 tabs（全部/PENDING_APPROVAL/DRAFT/ACTIVE/REJECTED/ARCHIVED）→ 表格（name/description/status tag/source tag/qualityScore/version/操作）→ 详情 modal（marked 渲染 content + 元数据）→ 新建/编辑 modal → 生成 modal（intent textarea → 调 generate → 显示结果） |
| `router/index.ts` + `layouts/MainLayout.vue` | 改动 | 加 `{ path: 'skill', name: 'Skill', component: SkillManageView }` 子路由 + 菜单「技能管理」项 |
| `package.json` | 改动 | 加 `marked`（轻量 markdown 渲染，~30KB） |

### 复用既有（不改）
- `SkillService.submitDraft`（Phase 3）：生成的草稿走同一打分+闸门+版本化 sink。
- 既有审批/物化/状态机端点：前端直接调。
- `SkillDataInitializer` 的 `generate-skill` 元技能种子：作为生成 prompt 的指令来源（从 DB 读，或内嵌模板）。

## 6. 关键决策

1. **生成放 skill 模块**（非复用 chat）：领域自洽（生成是技能域动作）、自包含；代价是给 skill 补 DeepSeek 能力（一个 starter + key）。复用 chat 会把生成逻辑错置到对话服务。
2. **生成与自主生成同 sink**：都走 `submitDraft`（打分+闸门+版本化），两条路径行为一致、打分可比。
3. **生成 prompt 用 `generate-skill` 元技能**：与 chat 自主生成用同一套指令，产出质量/格式一致；该元技能 Phase 3 已种为 ACTIVE。
4. **marked 渲染**：审自动/生成 SKILL.md 必须看渲染后的指令（frontmatter + 步骤 + 示例），远胜纯文本。
5. **默认生成模型 `deepseek-v4-flash`**：Phase 3 e2e 验证可用（function-calling + 稳定输出）；`skill.generate-model` 可配。

## 7. 生成服务细节

- **prompt**：system = `generate-skill` 元技能 content（从 DB `findByName("generate-skill")` 取，失败则内嵌兜底）；user = `"## 技能意图\n{intent}\n\n请严格按上面的指引起草一个 SKILL.md 并完整输出（含 frontmatter: name + description）。"`
- **解析**：正则 `^---\s*\nname:\s*(.+)\ndescription:\s*(.+)\n.*?\n---` 取 name/description；content = 模型完整输出。解析失败（无 frontmatter）→ name 用 slug(intent)、description 用 intent；交 `submitDraft`，由打分器判 REJECTED（frontmatter 缺失扣分）——不抛错，给人工可见的 REJECTED 草稿。
- **模型调用**：`ChatModel.call(Prompt)` 或 `ChatClient`；用 `skill.generate-model` 配的 modelCode 构造 `DeepSeekChatOptions`。超时复用 DeepSeek 默认（或 120s）。

## 8. 错误处理

- **模型调用失败/超时**：`SkillGenerationService` 抛运行时异常 → 经 `GlobalExceptionHandler` 映射 500；前端 toast「生成失败」。
- **frontmatter 解析失败**：降级为 slug(intent) 作 name，交打分器判 REJECTED（不阻断）。
- **前端请求失败**：各 api 调用 try/catch + toast，不崩溃页面。
- **生成草稿重名**：复用 `submitDraft` 的 V1 版本化（`-vN`）。

## 9. 测试（TDD）

- **`SkillGenerationServiceTest`**（skill，mock `ChatModel` + mock/spy `SkillService`）：模型返回规范 SKILL.md → 断言 `submitDraft` 被调且 name/description 取自 frontmatter、content 含 frontmatter；模型返回无 frontmatter → 降级 name=intent slug 且仍调 submitDraft。
- **`SkillControllerGenerateTest`**（skill，WebMvcTest + @MockBean）：POST `/api/skill/generate` {intent} + X-User-Id → 200，service 被委派。
- **前端**：无单测基建；手动 e2e（见下）。

**端到端（手动）**：启动全栈；前端技能页 → 列表见 greeting/generate/commit-message-skill → 点「生成技能」输入意图（如"代码审查清单"）→ 草稿进 PENDING_APPROVAL 队列（含打分）→ 详情 modal marked 渲染 → 审批通过 → 物化 → 下一轮 chat 可复用。

## 10. 范围边界（Phase 4 不做）

- chat/rag/workflow 前端技能化。
- LLM-as-judge 打分（仍是启发式）。
- 版本血缘可视化、技能调用统计、试运行（trial）UI。
- Ollama 生成。

## 11. 风险

1. **模型输出格式不稳定**：frontmatter 可能缺失/畸形 → 降级 + 打分器 REJECTED 兜底，不阻塞。
2. **skill 模块新增 DeepSeek 依赖体积**：一个 starter，可接受；与 chat 同款已验证。
3. **生成成本**：每次「生成技能」一次 LLM 调用；属用户主动操作，成本可控。
4. **api-key 重复配置**：chat 与 skill 各持一份 `spring.ai.deepseek.api-key`；可后续抽到 Nacos 共享，本轮接受重复（与既有 chat 内联 key 一致）。

## 12. Self-Review 结论

- **占位符**：无 TBD/TODO。
- **一致性**：组件清单、数据流、生成细节、测试全文对齐；后端类名/端点（`SkillGenerationService.generate`、`POST /api/skill/generate`、`GenerateSkillRequest`）与既有 `submitDraft` sink 一致；前端 api/类型/view 命名遵循 `api/model.ts`/`ModelConfigView.vue` 既有模式。
- **范围**：单一可交付单元（技能管理 UI + 生成后端），其他显式划出。
- **关键风险**：模型输出格式（降级+打分兜底）、DeepSeek 依赖（已验证）——均可覆盖。
