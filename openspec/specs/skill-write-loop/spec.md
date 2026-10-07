---
title: Agent Skill Phase 3 — 写侧自进化闭环
description: 补上写侧完成自进化闭环：模型判定无匹配技能时自主起草 SKILL.md 并经 submit_skill_draft 工具写回 skill 服务进审批队列。
status: active
---

## Purpose

Phase 1 管理面与 Phase 2 chat 读侧已完成。本阶段补上写侧，使模型在技能目录无匹配时自主加载元技能 `generate-skill`，按模板起草新技能 SKILL.md，通过 `submit_skill_draft` 工具写回 skill 服务经启发式打分自动流转，审批通过后物化并在下一轮对话中自动可见，完成自进化闭环。

## Requirements

### Requirement: The system SHALL provide a submit_skill_draft tool for model-initiated skill creation

The system SHALL register a SubmitSkillDraftTool that POSTs drafted SKILL.md to aicoder-skill /api/skill/draft without interrupting the conversation. 模型在判定技能目录无匹配时调用该工具提交草稿。

#### Scenario: 模型自主提交技能草稿成功
- **WHEN** 模型调用 `submit_skill_draft("code-review-checklist", "PR 代码审查清单", "<完整 SKILL.md 正文>")` 且 skill 服务正常响应
- **THEN** 工具返回确认串（包含技能状态 PENDING_APPROVAL / REJECTED 及可能的版本化名称），当前对话轮正常继续

#### Scenario: skill 服务不可用时降级
- **WHEN** 模型调用 submit_skill_draft 但 skill 服务不可达或超时
- **THEN** `SubmitSkillDraftTool` 捕获 RestTemplate 异常，返回友好提示串如"技能提交失败：...，请稍后重试"，不中断当前对话轮

#### Scenario: 非法技能名本地拒绝
- **WHEN** 模型传入非 kebab-case 格式或包含路径穿越字符的技能名
- **THEN** `SubmitSkillDraftTool` 在本地校验阶段直接拒绝，不向 skill 服务发请求

### Requirement: The system SHALL provide a generate-skill meta-skill as a seed in the shared directory

The system SHALL materialize generate-skill as an ACTIVE seed skill containing trigger conditions, drafting method, SKILL.md template, and quality standards. 模型通过 `read_skill("generate-skill")` 加载后按指令起草新技能。

#### Scenario: 元技能种子在服务初始化时物化
- **WHEN** skill 服务启动且 `SkillDataInitializer` 执行
- **THEN** `generate-skill` 种子（含完整 SKILL.md 指令）被物化到共享目录，幂等（已存在则跳过），chat 侧通过 FileSystemSkillRegistry 可感知

#### Scenario: 模型加载元技能起草新技能
- **WHEN** 模型判定目录无匹配技能且任务为可复用的重复性模式
- **THEN** 模型调用 `read_skill("generate-skill")` 加载元技能指令，按其中的模板（frontmatter + 指令步骤 + 示例）起草新 SKILL.md

#### Scenario: 一次性任务不触发生成
- **WHEN** 模型判定当前任务为一次性问题不会重复出现
- **THEN** 元技能指令中的约束引导模型不生成新技能，直接作答

### Requirement: The system SHALL apply heuristic quality scoring to auto-generated skill drafts

The system SHALL score drafts on kebab-name validity, description length, frontmatter completeness, and body length, rejecting those below qualityThreshold. 分数低于阈值的草稿自动 REJECTED 不进审批队列。

#### Scenario: 规范草稿高分通过
- **WHEN** 草稿具有规范的 kebab-case 名称、10-200 字描述、合法 frontmatter（成对 `---` + 含 name/description）、正文剥离 frontmatter 后超过 50 字
- **THEN** `SkillQualityScorer` 返回分数 >= 60，草稿自动流转为 PENDING_APPROVAL

#### Scenario: 低质草稿被拒绝
- **WHEN** 草稿名称非 kebab-case、描述缺失或过短、frontmatter 缺失或畸形、正文过短
- **THEN** `SkillQualityScorer` 返回分数 < 60，草稿直接置为 REJECTED（保留 intent 供人工查看），不进入 PENDING 队列

#### Scenario: 质量阈值可通过 Nacos 配置调整
- **WHEN** 管理员在 `aicoder-shared.yml` 中修改 `skill.qualityThreshold` 值
- **THEN** skill 服务的 `QualityProperties` 读取新阈值，后续打分按新阈值判定

### Requirement: The system SHALL handle duplicate skill names with versioned suffix naming

The system SHALL auto-version duplicate names by appending -v2/-v3 suffixes with parent_skill_id lineage. ai_skill.name 有 UNIQUE 约束时的确定性版本化处理。

#### Scenario: 新名称直接创建
- **WHEN** 提交的 skill name 在数据库中不存在
- **THEN** `submitDraft` 以原 name 创建，version=1，parentSkillId=null

#### Scenario: 重名自动加版本后缀
- **WHEN** 提交的 name `greeting-skill` 已存在（v1）
- **THEN** `submitDraft` 自动创建 `greeting-skill-v2`（version=2，parentSkillId 指向 v1），若 `-v2` 也已存在则扫描至 `-v3` 依此类推

#### Scenario: 新旧版本并存于目录
- **WHEN** 新版本技能审批通过并物化
- **THEN** 新旧版本以不同目录名（`greeting-skill` 和 `greeting-skill-v2`）并存于共享目录，旧版的 archive 由审批者手动操作

### Requirement: The system SHALL seamlessly close the self-evolution loop via per-request registry reload

The system SHALL rely on the Phase 2 advisor's per-request reload() for new skills to appear automatically in the next conversation turn. 无需独立 Notifier。

#### Scenario: 审批通过后下一轮对话自动可见
- **WHEN** 管理员审批通过一个技能并物化到共享目录
- **THEN** chat 服务的下一次请求中 `SkillPromptAugmentAdvisor` 经 reload 感知新技能，模型可直接匹配复用

#### Scenario: 闭环完整流程
- **WHEN** 模型在对话中触发 generate-skill → submit_skill_draft → 审批通过 → 物化
- **THEN** 下次类似提问时新技能已在目录中，模型直接匹配 `read_skill` 加载复用，无需重新生成

### Requirement: The system SHALL transmit user identity to skill service via ToolContext

The system SHALL inject userId via Spring AI ToolContext into SubmitSkillDraftTool and forward it as X-User-Id header. 使技能草稿记录作者信息。

#### Scenario: ToolContext 可用时透传 userId
- **WHEN** `ChatService` 在构建 ChatClient 时通过 `.toolContext(Map.of("userId", userId))` 注入当前用户 ID
- **THEN** `SubmitSkillDraftTool` 从 `ToolContext` 取出 userId 并放入 POST 请求的 `X-User-Id` 头，skill 服务记录 `author_user_id`

#### Scenario: ToolContext 不可用时降级
- **WHEN** Spring AI 版本不支持 `@Tool` + `ToolContext` 组合
- **THEN** `SubmitSkillDraftTool` 降级为 userId=null，草稿 author 留空，不影响闭环主流程
