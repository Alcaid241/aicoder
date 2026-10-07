# Agent Skill 体系 — Phase 3：写侧自进化闭环设计

> **关联：** 系统设计 [2026-06-13-agent-skill-system-design.md](2026-06-13-agent-skill-system-design.md)（§5 数据流、§7 组件、§10 风险）、Phase 1 管理面 [2026-06-13-agent-skill-phase1-management-plane.md](../plans/2026-06-13-agent-skill-phase1-management-plane.md)、Phase 2 读侧 [2026-06-15-agent-skill-phase2-chat-runtime-design.md](2026-06-15-agent-skill-phase2-chat-runtime-design.md)。

## 1. 背景

Phase 1（管理面 aicoder-skill :8086）与 Phase 2（chat 读侧）已完成：ACTIVE 技能经 Nacos 共享 `skill.directory` 物化，chat 经 `SkillPromptAugmentAdvisor` 每请求注入目录 + 自建 `read_skill` 工具按需加载。Phase 2.5（commit `be9708f`/`9d1e139`）已闭环两个 Phase-3 前置项（禁用 `~/saa/skills` 扫描、`historyMessages` 去重）。

本阶段（Phase 3）补上**写侧**，完成自进化闭环：模型判定目录无匹配技能、且任务可复用时，自主起草 SKILL.md 并经 `submit_skill_draft` 工具写回 skill 服务，进审批队列。

## 2. 本轮范围（已与用户确认）

- **后端闭环**：`submit_skill_draft` 工具 + skill 侧 draft 端点 + `generate-skill` 元技能种子 + 启发式质量打分。前端审批页延后。
- **启发式打分**：确定、无额外 LLM 调用。低于阈值直接 REJECTED。
- **模型自主触发**：模型自主判定"无匹配"并生成；`generate-skill` 元技能指令约束"仅对可复用的重复性任务"生成以降噪。
- **重名自动版本化（V1）**：版本后缀名（见 §7）。

**本轮不做（YAGNI）**：前端审批页；`SkillGenerationService`（服务端 LLM 生成，仅前端"意图→草稿"入口需要，随前端延后）；`SkillReloadNotifier`（Phase 2 advisor 每请求 `reload()` 已覆盖热刷新）；LLM-as-judge 打分；rag/workflow 接入。

## 3. 调研结论

| 事实 | 来源/校验 |
|---|---|
| chat 工具调其它服务的既有模式是 `@LoadBalanced RestTemplate` → `http://aicoder-<svc>/api/...`（SqlQueryTool/KnowledgeSearchTool 已用） | chat 模块代码 |
| `aicoder-skill` 状态机/审批/物化/`listPending` 全已就绪；缺"草稿写回"专用端点（`/submit` 只对已存在 id 做 DRAFT→PENDING） | SkillController/SkillService |
| `ai_skill.name` 有 `UNIQUE KEY uk_skill_name`（schema.sql:219），与设计 §10.4"同名版本化"字面冲突 → 采用 V1 版本后缀名 | schema.sql |
| Spring AI `@Tool` 方法可声明 `ToolContext` 形参，由 `.toolContext(Map)` 注入，模型不感知 → 用它把 userId 透传进 submit 工具 | Spring AI 1.1.2 API |
| Phase 2 advisor 每请求 `reload()` 重扫共享目录 → 审批通过并物化的新技能，下一轮对话自动可见，**无需 Notifier** | Phase 2 设计 §2 + memory |

## 4. 架构与数据流

```
chat 对话轮（advisor 已注入 ACTIVE 技能目录，含 generate-skill 元技能）
  ├─ 匹配某技能 → read_skill（Phase 2 既有）
  └─ 无匹配 + 可复用任务 → read_skill("generate-skill") → 模型按其模板起草 SKILL.md
        → 调 submit_skill_draft(name, description, content) 工具
        → RestTemplate POST http://aicoder-skill/api/skill/draft  (header: X-User-Id)
              ↓
skill 服务 submitDraft()（单 @Transactional）：
  ① 重名 → 版本后缀化（§7 V1）
  ② 建 Skill(status=DRAFT, source=AUTO_GENERATED, author_user_id=userId)
  ③ SkillQualityScorer 启发式打分 → quality_score
  ④ score ≥ skill.quality-threshold(默认60) → PENDING_APPROVAL；否则 → REJECTED
下一轮对话：advisor 每请求 reload() → 已审批 ACTIVE 技能自动进目录（闭环完成）
```

## 5. 组件清单

### chat（`com.ai.coder.chat`）

| 组件 | 改动 | 职责 |
|---|---|---|
| `tool/SubmitSkillDraftTool` | 新增 | `@Component`，`@Tool submitSkillDraft(String name, String description, String content, ToolContext ctx)`：本地 kebab 名校验（复用 ReadSkillTool 的防穿越/格式守卫）→ RestTemplate POST `/api/skill/draft`，header 带 `X-User-Id`（取自 ctx.get("userId")）。失败/异常返回友好串（不中断对话轮），成功返回确认串（含状态：PENDING_APPROVAL/REJECTED/版本化名） |
| `service/ChatService` | 改动 | 注入 `SubmitSkillDraftTool`；`buildSkillClient` 的 `.defaultTools` 追加它；`chat()`/`chatStream()` 的 prompt 链加 `.toolContext(Map.of("userId", userId))` |

### skill（`com.ai.coder.skill`）

| 组件 | 改动 | 职责 |
|---|---|---|
| `dto/SkillDraftRequest` | 新增 | `{name, description, content}`（author 取自请求头 `X-User-Id`） |
| `service/SkillQualityScorer` | 新增 | `ScoreResult score(name, description, content)` → `{score:int(0-100), rationale:String}`。启发式：kebab 名(+25)/描述 10–200 字(+25)/frontmatter 合法（成对 `---` + 含 name/description）(+25)/正文剥离 frontmatter 后 ≥50 字(+25)。纯函数，无 IO |
| `service/SkillService.submitDraft(...)` | 新增方法 | 入参 `(name, description, content, authorUserId)`，单事务：重名版本化（§7）→ 建 DRAFT(AUTO_GENERATED) → 打分写 `qualityScore` → 阈值流转（≥threshold→PENDING_APPROVAL，否则 REJECTED）→ 返回 Skill |
| `controller/SkillController` | 改动 | 加 `POST /api/skill/draft`（@RequestBody SkillDraftRequest + `X-User-Id` 头）→ `skillService.submitDraft(...)`。复用现有 GlobalExceptionHandler 映射 |
| `init/SkillDataInitializer` | 改动 | 加第 2 个 ACTIVE 种子技能 `generate-skill`（元技能 SKILL.md，见 §6），物化到共享目录；幂等（existsByName 跳过） |
| `config`（SkillProperties 或新 QualityProperties） | 改动 | `qualityThreshold` 默认 60，经 `aicoder-shared.yml` 可调（@Value 或 @ConfigurationProperties） |

### 复用既有（不改）

- `SkillRegistrySyncService.materialize`：审批（`approve`）时落盘——新增技能经既有审批流自动物化。
- 状态机/审批端点（`listPending`/`approve`/`reject`/`archive`）：Phase 1 已就绪。
- Phase 2 `SkillPromptAugmentAdvisor` 的每请求 `reload()`：热刷新天然覆盖。

## 6. 元技能 `generate-skill/SKILL.md`（种子内容要点）

```markdown
---
name: generate-skill
description: 当技能目录中没有任何技能能匹配用户任务、且该任务是可复用的重复性模式时使用——起草一个新的标准化技能并提交审批。
---
# Generate Skill（元技能）

## 何时使用
- 目录中无匹配技能，**且**判断该任务以后会重复出现（一次性问题不要生成）。

## 如何起草
1. 给技能起 kebab-case 名（短、表意，如 `code-review-checklist`）。
2. 写一句话 description（何时使用）。
3. 按 SKILL.md 模板写正文：frontmatter(name+description) + 指令步骤 + 必要示例。

## 质量标准（低质会被自动拒绝）
- 名字规范、描述清晰、指令可执行、有示例。

## 完成动作
调用工具：submit_skill_draft(name, description, content)
（content 为完整 SKILL.md 文本，含 frontmatter。）
```

> 触发约束"仅可复用重复性任务"写在元技能指令里，是降噪的第一道闸门；启发式打分是第二道；人工审批是第三道。

## 7. 版本化（V1：版本后缀名）

`ai_skill.name` 为 UNIQUE。重名草稿处理（确定性算法，避免 `-v2` 已存在时二次冲突）：

- 入参 `name` 不存在（`!existsByName(name)`）：`name` 不变、`version = 1`、`parentSkillId = null`。
- 入参 `name` 已存在：取该行为 `original`（`parentSkillId = original.id`），并选取**最小**的 N≥2 使得 `name + "-v" + N` 不存在（从 N=2 起自增扫描既有名），新草稿 `name = name + "-v" + N`、`version = N`。
  - 例：`greeting-skill`(v1) 在 → 新草稿 `greeting-skill-v2`(v2)；若 `-v2` 也在 → `greeting-skill-v3`(v3)。
- 新草稿进 PENDING_APPROVAL 后，与旧版同名并存于目录（如 `greeting-skill` 与 `greeting-skill-v2`）；旧版的 archive 由审批者经既有 `archive` 端点决定（本轮不做自动替换，YAGNI）。

血缘由 `parent_skill_id` + `version` 表达，不改 schema、零迁移。

## 8. 错误处理

- **RestTemplate 调 skill 失败/超时**：`SubmitSkillDraftTool` 捕获，返回友好串（"技能提交失败：…"），**不中断**当前对话轮。
- **kebab 名非法/内容为空**：工具侧先做轻校验拒绝明显坏输入；其余交打分器判 REJECTED。
- **打分 < 阈值**：草稿存为 REJECTED（保留 intent 供人工查看/重提），不进队列。
- **skill 服务校验异常**：经既有 `GlobalExceptionHandler` 映射（400/409/500）。

## 9. 测试（TDD）

- **`SkillQualityScorerTest`**：规范草稿高分（≥阈值）；名字非 kebab 扣分；无/畸形 frontmatter 扣分；正文过短扣分；总分与 rationale 断言。
- **`SubmitSkillDraftToolTest`**：mock RestTemplate——成功返回含状态确认的串；RestTemplate 抛异常→友好串（不抛出）；非法名→本地拒绝（不发请求）。
- **`SkillServiceSubmitDraftTest`**（H2 slice 或 mock repo+scorer）：新名→PENDING_APPROVAL 且 qualityScore 已写；重名→`-v2`+parentSkillId+version=2；低分→REJECTED。
- **`SkillControllerDraftTest`**（MockMvc 或 WebMvcTest slice）：POST `/api/skill/draft` → 200 + Skill；缺字段→400。
- **端到端（手动，需中间件）**：启动 chat+skill+gateway+Nacos；用支持 function-calling 的模型，问一个目录里没有、但可复用的任务（如"每次都帮我给 PR 写 commit message"）→ 应触发 read_skill("generate-skill") + submit_skill_draft → skill 侧出现 PENDING_APPROVAL 草稿（或 REJECTED，看打分）；人工 `PUT /api/skill/{id}/approve` → 物化 → 下一轮该技能进目录可复用。

## 10. 范围边界（Phase 3 不做）

- 前端技能管理/审批页（§9 of 系统设计）。
- `SkillGenerationService`（服务端 LLM 从意图生成）——随前端延后。
- `SkillReloadNotifier`——Phase 2 advisor 每请求 reload 已覆盖。
- LLM-as-judge 质量打分——本轮启发式；LLM-judge 留作质量提升迭代。
- rag/workflow 技能接入——同构，后续。
- 版本审批自动 archive 旧版——本轮人工 archive。

## 11. 风险

1. **模型不触发 / 过度触发**：元技能指令约束 + 启发式打分 + 人工审批三道闸门。过度触发时，REJECTED 草稿不淹没 PENDING 队列。
2. **ToolContext 可用性**：Spring AI 1.1.2 支持 `@Tool` + `ToolContext`；实现时验证 API，若不可用降级为 userId=null（草稿 author 留空）。
3. **自主生成成本**：每轮可能多一次 read_skill + 一次工具调用往返；可接受（与 Phase 2 read_skill 同量级）。
4. **RestTemplate 跨服务依赖**：skill 服务不可用时 submit 工具降级为提示串，不影响对话主路径。

## 12. Self-Review 结论

- **占位符**：无 TBD/TODO。
- **一致性**：组件清单、数据流、版本化、测试全文对齐；类名/方法名（`SubmitSkillDraftTool.submitSkillDraft`、`SkillQualityScorer.score`、`SkillService.submitDraft`、`POST /api/skill/draft`）一致。
- **范围**：单一可交付单元（写侧闭环），前端/LLM-judge/Notifier/GenerationService 显式划出。
- **关键风险**：模型触发频率、ToolContext 可用性——均可由测试与降级覆盖。
