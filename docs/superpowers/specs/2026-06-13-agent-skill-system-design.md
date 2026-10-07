# Agent Skill 自进化体系设计

> 状态：设计稿（仅设计，未实现）
> 日期：2026-06-13
> 作者：haijingxu
> 关联模块：新增 `aicoder-skill` (:8086)；改动 `aicoder-chat`（运行面）、`aicoder-web`（管理页）、Gateway（路由）

## 1. 背景与目标

在现有 chat / rag / workflow 微服务之上，建立一套「智能对话匹配 → 缺失则生成 → 验证固化 → 复用迭代」的技能自增长闭环，使 AI 能力以标准化、可发现、可复用的模块形式沉淀，而非一次性写死在提示词或硬编码工具里。

### 1.1 核心理念

- **技能工程（Skill Engineering）**：将 AI 能力封装为标准化、可发现、可按需加载的技能模块。
- **渐进式披露（Progressive Disclosure）**：智能体初始只感知精简目录（名称 + 一句话描述），匹配时再加载完整内容，降低 Token 消耗、避免工具过载。
- **元技能（Meta-Skill）自进化**：把"生成新技能"这一能力本身也实现为一个技能，形成「检索 → 生成 → 验证 → 固化 → 复用」的自增长闭环。

### 1.2 关键设计决策（已与用户确认）

| 维度 | 决定 | 理由 |
|---|---|---|
| 技能本质 | **纯提示词技能**（SKILL.md：指令 + 上下文 + 参考资料） | 生成安全（产出是文本）、验证容易、固化成本低，无代码沙箱风险 |
| 存储 / 发现 | **文件系统原生**，Alibaba `SkillRegistry` 扫描 | 最贴近开放标准、享受原生渐进式披露 |
| 匹配 / 缺失检测 | **模型原生判断**（SkillsAgentHook 注入目录 + read_skill） | 零额外基础设施 |
| 生成自主度 | **生成自动 + 固化需审批**（带质量打分） | 生成快、固化可控，最贴合"完成后固化" |

## 2. 调研校验（Spring AI / Spring AI Alibaba）

| 结论 | 校验 |
|---|---|
| Spring AI Alibaba 内置 Skill 体系（SkillRegistry / SkillsAgentHook / read_skill / SKILL.md） | ✅ 正确。位于 **agent-framework** 模块。`SkillRegistry` 有 `ClasspathSkillRegistry`/`FileSystemSkillRegistry` 实现；`SkillsAgentHook` 自动注册 `read_skill` 工具并把技能元数据注入系统提示；`SkillScanner` 扫描 `SKILL.md`。官方说明"借鉴了 Claude Skills"。 |
| Spring AI 官方 `spring-ai-agent-utils` 引入 Agent Skills | ⚠️ 方向正确，但成熟开箱实现在 Alibaba agent-framework；Spring 官方有通用 Agent Skills 概念（2026-01 博客）。 |
| FunctionToolCallback / 运行时动态工具 / MCP | ✅ 正确。Spring AI 1.1 用 `ToolCallback` 体系（替代废弃 `FunctionCallback`）；`FunctionToolCallback`（BiFunction）、`ToolCallbackResolver`、MCP 运行时增删均准确。 |
| 「自动生成并固化」无开箱方案 | ✅ 正确。需自研闭环。 |

**结论**：匹配/渐进式披露/加载这一半用 Alibaba 原生能力；「缺失则生成 → 验证 → 固化 → 复用」元技能闭环是自研核心。

## 3. 架构总览：双面分离

```
                         ┌──────────────── 管理面 ────────────────┐
   aicoder-web (:8888)   │   aicoder-skill (:8086)                 │
   技能管理页 ──/api/skill/**──▶  • SkillController (CRUD/审批/生成) │
                              │   • SkillGenerationService (LLM 生成)│
                              │   • SkillQualityScorer (试运行打分)  │
                              │   • SkillLifecycleService (状态机)   │
                              │   • SkillRegistrySyncService ◀──物化 │
                              └──────────────┬────────────────────────┘
                                             │ 写 SKILL.md + 触发 reload
                                             ▼
                          ┌─────────── 共享技能目录 (挂载卷) ───────────┐
                          │  skills/                                  │
                          │    generate-skill/SKILL.md  (元技能)       │
                          │    <其他 ACTIVE 技能>/SKILL.md             │
                          └─────┬──────────────┬──────────────┬────────┘
                                │ 原生扫描      │              │
                      ┌─────────▼──┐  ┌────────▼───┐  ┌───────▼──────┐
   运行面             │ chat :8082 │  │ rag :8083  │  │ workflow:8084│
                      │ SkillReg.  │  │ SkillReg.  │  │ SkillRegistry│
                      │ +Hook      │  │ +Hook      │  │ +Hook        │
                      └─────────────┘  └────────────┘  └──────────────┘
```

- **管理面** `aicoder-skill`：新微服务，与 admin/system 同构（Spring Boot 3.5 + Nacos 注册 + Gateway 路由）。负责技能全生命周期管理、生成编排、质量打分、审批、把 ACTIVE 技能物化为 SKILL.md 写入共享目录并触发运行面重载。
- **运行面** chat/rag/workflow：各自实例化 Alibaba 原生 `SkillRegistry` 指向同一共享目录，用 `SkillsAgentHook` 把目录注入系统提示，模型原生 `read_skill` 按需加载。**运行面只读共享目录，不直接读写 DB。**

> 自洽点：生成能力以「元技能（指令文本） + 应用级 `submit_skill_draft` 工具」实现，符合"纯提示词技能"——技能内容是指令，提交工具是普通应用工具而非技能内置代码。

## 4. 技能生命周期状态机

只有 `ACTIVE` 技能被物化到共享目录、进入运行面目录；其余状态仅存 DB。

```
   (模型原生判定"缺失")
            │ 调用 generate_skill 元技能 + submit_skill_draft 工具
            ▼
        ┌────────┐  自动质量打分   ┌─────────────────┐  审批通过+物化  ┌────────┐
        │ DRAFT  │ ──────────────▶ │PENDING_APPROVAL │ ─────────────▶ │ ACTIVE │
        └────────┘                 └─────────────────┘                 └────────┘
            │ 打分<阈值/校验失败        │ 人工拒绝                            │ 编辑迭代
            ▼                          ▼                                   ▼
        REJECTED                  REJECTED                        新版本 DRAFT ──…──▶ ACTIVE
                                                                      │ 归档
                                                                      ▼
                                                                   ARCHIVED
```

`source` 区分 `AUTO_GENERATED`（元技能产出）与 `MANUAL`（管理页手建），两者走同一状态机。

## 5. 核心闭环数据流

1. 用户在 chat 提问 → `SkillsAgentHook` 已把 ACTIVE 技能目录注入系统提示。
2. 模型评估：**匹配** → `read_skill(name)` 加载 → 套用技能作答。
3. **不匹配** → 模型调用元技能 `generate-skill` → 按 SKILL.md 模板起草新技能 → 调用 `submit_skill_draft(name, description, content)` 工具（普通 `FunctionToolCallback`，注册在 chat）→ 请求打到 skill 服务 → 存为 DRAFT + 触发自动打分 → 转 `PENDING_APPROVAL`。
4. 管理员在审批队列审核 → 通过。
5. skill 服务把该 SKILL.md 物化到共享目录 → 通知运行面重载注册表。
6. 下一轮对话：新技能已进目录，模型原生匹配复用 → **闭环完成**。

## 6. 数据模型

### 6.1 `ai_skill`（DB 为唯一真相源；文件是物化产物）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR UNIQUE | kebab-case slug，即技能目录名 |
| display_name | VARCHAR | 展示名 |
| description | VARCHAR | 一句话描述，被 SkillsAgentHook 注入目录 |
| content | LONGTEXT | 完整 SKILL.md 正文 |
| version | INT | 默认 1 |
| status | ENUM | DRAFT / PENDING_APPROVAL / ACTIVE / REJECTED / ARCHIVED |
| source | ENUM | MANUAL / AUTO_GENERATED |
| category | VARCHAR | 分组（可空） |
| tags | VARCHAR | 标签（可空） |
| quality_score | DECIMAL | 打分器产出 0–100（可空） |
| trial_result | JSON | 试运行样例 prompt→输出 + 评判备注（可空） |
| file_path | VARCHAR | 物化路径，ACTIVE 前为 null |
| parent_skill_id | BIGINT nullable | 版本血缘/迭代链 |
| author_user_id | BIGINT | 取自 X-User-Id |
| approved_by | BIGINT nullable | 取自 X-User-Id |
| approved_at | DATETIME nullable | |
| created_at / updated_at | DATETIME | |

无 Flyway/Liquibase，建表 DDL 进 `sql/schema.sql`，运行时 Hibernate `ddl-auto: update` 同步。

### 6.2 延后表（YAGNI，留作"复用迭代"阶段）

- `ai_skill_version`：完整版本史。当前 `version` + `parent_skill_id` 已覆盖基本血缘。
- `ai_skill_usage`：调用分析（skill_id / conversation_id / invoked_at / feedback），支撑"复用迭代"度量。

## 7. `aicoder-skill` 组件清单（`com.ai.coder.skill`）

| 组件 | 职责 |
|---|---|
| `controller/SkillController` | REST：CRUD、listPending、approve/reject、generate(意图→草稿)、rescan |
| `dto/` | SkillDTO、SkillDraftRequest、ApproveRequest、GenerateRequest |
| `entity/Skill` + `repository/SkillRepository` | JPA，镜像 ai_skill |
| `service/SkillService` | CRUD + 编排 |
| `service/SkillLifecycleService` | 状态机，校验合法迁移 |
| `service/SkillGenerationService` | 用 DynamicModelRegistry 调 LLM，按模板产出 SKILL.md 草稿 |
| `service/SkillQualityScorer` | LLM-as-judge：对 N 个样例 prompt 试运行，打结构/清晰/安全分 |
| `service/SkillRegistrySyncService` | ACTIVE→写 SKILL.md；ARCHIVED/REJECTED→删文件 |
| `service/SkillReloadNotifier` | 通知运行面重载（REST `/internal/skills/reload` 或文件 watch） |
| `config/DynamicModelRegistry` | 复用其他模块同名模式 |
| 内置资源 `generate-skill/SKILL.md` | 元技能种子，首启物化到共享目录 |

### 7.1 元技能 `generate-skill/SKILL.md` 结构

```markdown
---
name: generate-skill
description: 当目录中没有任何技能能匹配用户任务时使用——起草一个新的可复用技能并提交审批。
---
# Generate Skill
<触发条件 / 任务分析方法 / SKILL.md 模板 / 质量标准>
完成后调用工具 submit_skill_draft(name, description, content)。
```

## 8. 运行面改动（chat 最小集）

- 注册 `FunctionToolCallback` `submit_skill_draft(name, description, content)` → POST `/api/skill/draft`。
- chat Agent 配置 `FileSystemSkillRegistry`（共享目录）+ `SkillsAgentHook`。
- 内部端点 `POST /internal/skills/reload`（热刷新降级方案）。
- rag / workflow 同构接入，列为 phase-2。

## 9. 前端（aicoder-web 管理页）

技能管理页：

- 列表（名/描述/状态/来源/质量分/版本）。
- Markdown 编辑 + SKILL.md 预览。
- **审批队列**（PENDING_APPROVAL，展示试运行结果，通过/拒绝）。
- "生成技能"入口（输入意图描述 → 触发生成 → 进队列）。
- 菜单/权限复用 system 模块 RBAC。

## 10. 前置依赖与风险

1. **版本依赖（关键）**：`ClasspathSkillRegistry`/`SkillsAgentHook` 成熟态在 Spring AI Alibaba **1.1.2.2+**（[#4426](https://github.com/alibaba/spring-ai-alibaba/issues/4426)）。项目 BOM 1.1.2.0、extensions 1.1.2.1，仅 workflow 用 graph-core 1.1.2.2。→ 实现前**必须**验证 API、按需升级 BOM 或引入 agent-framework 依赖。
2. **共享目录**：多服务访问同一路径。Docker 共享 volume；裸机约定如 `/data/aicoder/skills`，所有服务配同一路径。
3. **热刷新**：原生 registry 多为启动扫描，运行时新增可见性需验证；降级用 `/internal/skills/reload` + 文件 watch 或周期 rescan。
4. **重名 / 并发**：name 唯一约束；同名自动生成走版本化（parent_skill_id + version）而非报错。
5. **质量闸门**：打分 < 阈值不进 PENDING（REJECTED 或转人工），防低质草稿淹没队列。
6. **回滚**：ACTIVE 出问题 → ARCHIVED + 删文件 + reload；旧对话已加载上下文不撤销。

## 11. 错误处理

- 生成失败 / 超时 → DRAFT 标 failed，保留意图供重试。
- 物化写盘失败 → 事务回滚，状态不变。
- reload 通知失败 → 重试 + 日志；文件已就位则下次启动自然加载。

## 12. 范围说明

本轮**仅设计，不实现**。后续若决定落地，按本设计进入实现计划（writing-plans），并优先解决第 10 节的版本依赖与共享目录两项前置项。建议实现分两阶段：

- **阶段一**：`aicoder-skill` 管理面（CRUD + 手建技能 + 物化 + reload）+ chat 运行面接入（原生匹配/read_skill）。先把现有工具（SQL/HTTP/知识检索）固化为标准技能。
- **阶段二**：元技能自进化闭环（generate-skill + submit_skill_draft + 质量打分 + 审批队列 + 前端审批页）。
