# aicoder-skill 操作手册

> Agent Skill 自进化体系的管理面。本文覆盖 aicoder-skill 服务（`:8086`）的全部功能：技能全生命周期管理、质量打分闸门、版本化、自进化生成闭环、配置与运维排障。
>
> **关联设计**：[系统设计](superpowers/specs/2026-06-13-agent-skill-system-design.md)、[Phase 1 管理面](superpowers/plans/2026-06-13-agent-skill-phase1-management-plane.md)、[Phase 3 写侧闭环](superpowers/specs/2026-06-16-agent-skill-phase3-write-loop-design.md)、[Phase 4 前端+生成](superpowers/specs/2026-06-16-skill-mgmt-frontend-and-generation-design.md)。

---

## 1. 概述

`aicoder-skill` 是 Agent Skill 自进化体系的**管理面**：以 DB（`ai_skill` 表）为唯一真相源，把经审批的 `ACTIVE` 技能物化为 `SKILL.md` 写入共享目录，供 chat / rag / workflow 等运行面消费。它和运行面共同构成「**检索 → 生成 → 验证 → 固化 → 复用**」的自增长闭环：

```
chat 判定无匹配技能
   │ (自主) read generate-skill 元技能 → 起草 → submit_skill_draft
   │ (手动) 管理页「生成技能」输入意图 → POST /api/skill/generate
   ▼
aicoder-skill：建草稿 → 启发式打分 → 阈值闸门
   │  分≥阈值 → PENDING_APPROVAL     分<阈值 → REJECTED
   ▼
管理员审批 → APPROVE → 物化 SKILL.md 到共享目录
   ▼
下一轮对话：运行面 advisor 每请求 reload → 新技能进目录 → 复用（闭环完成）
```

**两种技能来源**：
- `MANUAL`：管理页手建 / API 创建。
- `AUTO_GENERATED`：chat 自主生成 或 管理页「生成技能」（LLM 产出）。

两者走**同一状态机**、同一打分闸门。

---

## 2. 架构与部署

| 项 | 值 |
|---|---|
| 服务名 | `aicoder-skill` |
| 端口 | `8086` |
| 注册 | Nacos（`localhost:8848`） |
| Gateway 路由 | `/api/skill/**` → `lb://aicoder-skill`（前端/外部经 Gateway :8080） |
| 内部直连 | 运行面（chat）经 `@LoadBalanced RestTemplate` → `http://aicoder-skill/api/skill/**`（绕过 Gateway） |
| 数据库 | MySQL `test_ai.ai_skill`（无 Flyway；`sql/schema.sql` DDL + Hibernate `ddl-auto:update`） |
| 缓存 | Redis |
| 共享配置 | Nacos `aicoder-shared.yml`（`skill.directory` 等） |
| 鉴权 | Gateway 校验 JWT → 注入 `X-User-Id` 头转发；服务侧各写端点读 `@RequestHeader("X-User-Id")`（可选） |

**依赖中间件**：MySQL（:3306）、Redis（:6379）、Nacos 3.x（:8848）。首次用 `sql/schema.sql` 建表。

---

## 3. 技能生命周期状态机

只有 `ACTIVE` 技能被物化到共享目录、进入运行面目录；其余状态仅存 DB。

```
        ┌────────┐  提审 submit   ┌─────────────────┐  审批通过+物化 approve ┌────────┐
新建 ──▶│ DRAFT  │ ─────────────▶ │PENDING_APPROVAL │ ────────────────────▶ │ ACTIVE │
        └────────┘                 └─────────────────┘                       └────────┘
            │   打分<阈值/校验失败       │  人工拒绝 reject                        │ 编辑迭代 / 归档 archive
            │   (仅 submitDraft 路径)     ▼                                        ▼
            ▼                         REJECTED                              ARCHIVED
        REJECTED  ←─────────  REJECTED ──回到 DRAFT(reject 后改回 submit)──◀─────┐
                                                              ARCHIVED ─重新 approve─▶ ACTIVE
```

**合法状态迁移表**（`SkillLifecycleService` 校验，非法迁移返回 409）：

| 当前状态 | 允许迁往 |
|---|---|
| `DRAFT` | `PENDING_APPROVAL`、`ACTIVE`、`REJECTED` |
| `PENDING_APPROVAL` | `ACTIVE`、`REJECTED` |
| `ACTIVE` | `ARCHIVED` |
| `REJECTED` | `DRAFT`（改回草稿重新提审） |
| `ARCHIVED` | `ACTIVE`（归档技能可经审批重新发布） |

---

## 4. SKILL.md 规范

技能正文是**纯提示词**（Markdown），由 frontmatter + 正文组成。运行面按 frontmatter 的 `name`/`description` 注入目录、按需 `read_skill` 加载正文。

```markdown
---
name: <kebab-case 技能名>
description: <一句话：何时使用，10–200 字>
---
# <技能标题>

## 何时使用
<触发条件>

## 工作流程
1. ...
2. ...

## 示例
<示例>
```

**质量标准**（自动打分依据，见 §6）：kebab 名 / 描述 10–200 字 / 合法 frontmatter（成对 `---` 且含 `name:`+`description:`）/ 正文（剥 frontmatter 后）≥50 字。

---

## 5. REST API 参考

所有路径前缀 `/api/skill`。前端/外部经 Gateway（`:8080`）；内部测试可直连 `:8086`。`X-User-Id` 头由 Gateway 注入（可选）。

### 5.1 查询

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 全部技能列表 |
| GET | `/pending` | 仅 `PENDING_APPROVAL`（审批队列） |
| GET | `/{id}` | 单个技能详情（含完整 content） |

```bash
# 经 Gateway（带 JWT）
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/skill/pending
# 直连测试（无需 JWT）
curl http://localhost:8086/api/skill/pending
```

### 5.2 创建 / 编辑 / 删除

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/` | 手建技能（status=DRAFT, source=MANUAL）。请求体 `SkillDTO` |
| PUT | `/{id}` | 编辑（display/desc/content/category/tags） |
| DELETE | `/{id}` | 删除（若 ACTIVE 先删物化文件再删行） |

`SkillDTO`（创建/编辑载荷）关键字段：
```json
{
  "name": "code-review-checklist",
  "displayName": "代码审查清单",
  "description": "对代码变更做清单式审查并给修改建议。",
  "content": "---\nname: code-review-checklist\ndescription: ...\n---\n# ...",
  "category": "coding",
  "tags": "review,quality",
  "source": "MANUAL"
}
```

```bash
curl -X POST http://localhost:8086/api/skill -H "Content-Type: application/json" -H "X-User-Id: 1" -d '{...}'
```

### 5.3 审批流转

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/{id}/submit` | DRAFT → PENDING_APPROVAL（手建技能提审） |
| PUT | `/{id}/approve` | PENDING_APPROVAL → **ACTIVE + 物化 SKILL.md**（写共享目录） |
| PUT | `/{id}/reject` | PENDING_APPROVAL → REJECTED |
| PUT | `/{id}/archive` | ACTIVE → ARCHIVED（删物化文件） |

```bash
# 审批通过 → 物化生效
curl -X PUT http://localhost:8086/api/skill/8/approve -H "X-User-Id: 1
```

### 5.4 自进化写回（两条生成路径，同 sink）

| 方法 | 路径 | 说明 | 调用方 |
|---|---|---|---|
| POST | `/draft` | 提交草稿（name/description/content）→ 建草稿 + 打分 + 阈值闸门（PENDING/REJECTED）+ 重名版本化。**chat 自主生成的 sink** | chat 的 `submit_skill_draft` 工具 |
| POST | `/generate` | 输入意图 `{intent}` → 调 DeepSeek 按 `generate-skill` 元技能模板起草 → 剥围栏 → 解析 frontmatter → 走 `submitDraft` 同一闸门 | 管理页「生成技能」按钮 |

```bash
# 手动意图生成
curl -X POST http://localhost:8086/api/skill/generate \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"intent":"把自然语言表结构需求转成第三范式建表 SQL"}'
```

返回新建的 `Skill`（status 为 PENDING_APPROVAL 或 REJECTED，含 qualityScore）。

---

## 6. 质量打分闸门

`/draft` 与 `/generate` 产出的草稿都经 `SkillQualityScorer`（启发式，纯函数）打分：

| 维度 | 分值 | 判定 |
|---|---|---|
| 技能名 | 25 | 匹配 `^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$`（kebab-case） |
| 描述 | 25 | trim 后 10–200 字 |
| frontmatter | 25 | 剥前导空白后以 `---` 起、有闭合 `\n---`、块内含 `name:` + `description:` |
| 正文 | 25 | 剥 frontmatter 后 ≥50 字 |

满分 100。**阈值**由 `skill.quality-threshold`（默认 **60**）控制：
- `score ≥ 阈值` → `PENDING_APPROVAL`（进审批队列）
- `score < 阈值` → `REJECTED`（不进队列，保留草稿供人工查看/重提）

打分结果写入 `ai_skill.quality_score`，扣分原因见服务日志。

> 阈值经 Nacos `aicoder-shared.yml` 的 `skill.quality-threshold` 可热调（`@RefreshScope`），无需重启。

---

## 7. 重名版本化（V1）

`ai_skill.name` 有唯一约束。提交草稿时若 name 已存在，**自动版本后缀化**而非报错：

- name 不存在：`name` 不变、`version=1`、`parent_skill_id=null`。
- name 已存在：`parent_skill_id` 指向同名原技能，选取**最小** N≥2 使 `<name>-v<N>` 不存在（自增扫描），新草稿 `name=<name>-v<N>`、`version=N>`。
  - 例：`code-review` 已存在 → 新草稿 `code-review-v2`；若 `-v2` 也在 → `code-review-v3`。

血缘由 `parent_skill_id` + `version` 表达，**不改 schema**。新版本审批通过后与旧版同名并存于目录（如 `code-review` 与 `code-review-v2`），旧版归档由审批者经 `/archive` 决定。

---

## 8. 自进化生成闭环

### 路径 A：chat 自主生成（运行面触发）
1. chat 对话轮：advisor 注入 ACTIVE 技能目录（含 `generate-skill` 元技能）。
2. 模型判定**无匹配技能且任务可复用** → `read_skill("generate-skill")` → 按模板起草 SKILL.md → 调 `submit_skill_draft(name, description, content)` 工具。
3. 工具经 `@LoadBalanced RestTemplate` POST `http://aicoder-skill/api/skill/draft`（userId 经 `ToolContext` 透传到 `X-User-Id` 头）。
4. skill 服务：建草稿（`AUTO_GENERATED`）→ 打分 → 闸门（PENDING/REJECTED）。

### 路径 B：管理页意图生成（人工触发）
1. 管理页「✨ 生成技能」→ 输入意图 → `POST /api/skill/generate {intent}`。
2. `SkillGenerationService`：读 `generate-skill` 元技能作 system 指令 → 调 DeepSeek（`skill.generate-model`，默认 `deepseek-v4-flash`）→ **剥 ```` ```markdown ```` 围栏** → 解析 frontmatter name/description → 走 `submitDraft` 同一闸门。

### 闭环收口（两条路径共用）
5. 管理员在审批队列（`/pending` 或管理页）审核 → `approve` → skill 服务物化 `<name>/SKILL.md` 到共享目录。
6. **无需显式 reload 通知**：运行面 advisor 每请求 `reload()` 重扫共享目录 → 下一轮对话自动发现新 ACTIVE 技能并复用。

---

## 9. 配置项

`SkillProperties`（`@ConfigurationProperties(prefix="skill")`，`@RefreshScope`，经 Nacos `aicoder-shared.yml` 可热调）：

| 配置 key | 默认 | 说明 |
|---|---|---|
| `skill.directory` | `./skills` | 技能物化共享目录（相对 JVM 工作目录）。**生产应配绝对路径**，所有运行面服务指向同一物理目录 |
| `skill.quality-threshold` | `60` | 草稿打分闸门阈值（0–100） |
| `skill.generate-model` | `deepseek-v4-flash` | `/generate` 调用的 DeepSeek 模型 |
| `spring.ai.deepseek.api-key` | — | DeepSeek API key（同 chat） |
| `spring.ai.deepseek.chat.options.model` | `deepseek-v4-flash` | DeepSeek 默认模型 |

**Nacos 共享配置示例**（`aicoder-shared.yml`，所有服务读同一份）：
```yaml
skill:
  directory: /Users/haijingxu/workspace/claudeCode/aicoder/skills
  quality-threshold: 60
```

启动日志会高亮实际解析路径（`[skill] directory: configured='...' resolved='...'`）——**务必核对 resolved 为预期绝对路径**，避免 cwd 混淆。

---

## 10. 启动与播种

`SkillDataInitializer`（`CommandLineRunner`，`@Order(20)`）启动时幂等播种**两个 ACTIVE 种子技能**并物化：

| 种子 | 用途 |
|---|---|
| `greeting-skill` | 示例技能（问候） |
| `generate-skill` | **元技能**：教模型何时/如何起草新技能并调 `submit_skill_draft`。是自进化闭环的"生成能力"本身 |

每个种子由 `existsByName` 守卫，重启幂等跳过。物化失败（目录不可写/权限）时**优雅降级**：技能入库但 `file_path` 留空 + WARN 日志，**不阻断启动**。

---

## 11. 前端管理页

`aicoder-web` 技能管理页，路由 `/#/skill`（`SkillManageView.vue`）：

- **状态过滤 tabs**：全部 / 待审批 / 草稿 / 已生效 / 已拒绝 / 已归档。
- **列表**：技能名 / 描述 / 状态 tag / 来源 tag（自动/手动）/ 质量分 / 版本 / 操作。
- **查看**：详情 modal，`marked` 渲染 SKILL.md（标题/代码/表格）+ 元数据。
- **新建/编辑**：表单（name/displayName/description/content textarea）。
- **✨ 生成技能**：输入意图 → 调 `/generate`（120s 超时）→ 草稿进队列。
- **审批动作**（按状态条件显示）：DRAFT→提审、PENDING→通过/拒绝、ACTIVE→归档。

> **侧边栏菜单项暂未加**（菜单是 DB 驱动的 RBAC `ai_menu`，需播种或用既有「菜单管理」UI 加）。页面经 `/#/skill` 直接可达。

---

## 12. 运维与排障

| 现象 | 原因 / 处置 |
|---|---|
| 启动 WARN「物化失败」、技能 `file_path` 为 null | `skill.directory` 不可写/权限不足/cwd 混淆。核对启动日志 resolved 路径，配绝对路径。技能已入库，修权限后重启或重新 approve 触发物化 |
| 草稿总是 REJECTED、quality_score 低 | 看打分器扣分项：名非 kebab / 描述不在 10–200 / 缺 frontmatter / 正文 <50。元技能模板已写明质量标准，让生成方遵循 |
| chat 调 `/draft` 报 `UnknownHostException: aicoder-skill` | chat 缺 `spring-cloud-starter-loadbalancer`（`@LoadBalanced` 不解析服务名）。已修复；rag/workflow 接入时同样要补 |
| `/generate` 返回的 name 是 slug（如 `sql`）而非 frontmatter 名 | 模型输出被 ```` ```markdown ```` 围栏包裹。`SkillGenerationService.stripCodeFences` 已剥围栏；若复现检查该逻辑 |
| Nacos 配置（directory/threshold）未生效 | nacos-client 与 nacos-server 版本需匹配（client 3.0.3 需 server 3.x）。见 [Nacos 3.x 升级记录] |
| 审批通过但 chat 下一轮没看到新技能 | advisor 每请求 reload，确认共享目录文件已写、chat 的 `skill.directory` 与 skill 指向同一绝对路径 |
| 重名提交报错而非版本化 | `/draft` 与 `/generate` 才走版本化；`POST /`（手建）遇重名直接 409 `DuplicateSkillException`（设计如此，手建要求唯一名） |

**关键日志关键字**：`已播种示例技能`（播种）、`已物化技能` / `materialize`（物化）、`生成技能草稿：intent='...' → name='...'`（生成）、`submit_skill_draft 提交`（chat 写回，在 chat 侧日志）。

---

## 13. 数据模型（`ai_skill` 表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR(64) UNIQUE | kebab-case，即技能目录名 |
| display_name | VARCHAR(128) | 展示名 |
| description | VARCHAR(512) NOT NULL | 一句话，注入运行面目录 |
| content | LONGTEXT NOT NULL | 完整 SKILL.md（含 frontmatter） |
| version | INT | 版本号 |
| status | ENUM | DRAFT/PENDING_APPROVAL/ACTIVE/REJECTED/ARCHIVED |
| source | ENUM | MANUAL/AUTO_GENERATED |
| category / tags | VARCHAR | 分组 / 标签（可空） |
| quality_score | DECIMAL(5,2) | 打分器产出 0–100（可空） |
| trial_result | TEXT | 试运行结果（预留，可空） |
| file_path | VARCHAR(512) | 物化路径，ACTIVE 前为 null |
| parent_skill_id | BIGINT | 版本血缘 |
| author_user_id | BIGINT | 作者（X-User-Id） |
| approved_by / approved_at | | 审批人 / 时间 |
| created_at / updated_at | | |

---

## 附录：典型操作速查

```bash
# 看审批队列
curl localhost:8086/api/skill/pending

# 手动意图生成一个技能
curl -X POST localhost:8086/api/skill/generate -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"intent":"帮我给代码改动写约定式 commit message"}'

# 审批通过（假设 id=8）→ 物化生效
curl -X PUT localhost:8086/api/skill/8/approve -H "X-User-Id: 1"

# 归档（假设 id=8 ACTIVE）
curl -X PUT localhost:8086/api/skill/8/archive

# 调整质量阈值（Nacos aicoder-shared.yml 改 skill.quality-threshold，@RefreshScope 自动生效）
```
