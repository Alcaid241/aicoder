---
title: Agent Skill 体系 — 技能管理前端 + 意图生成后端
description: 为技能自进化闭环补齐管理 UI（列表/审批/markdown 预览）与服务端 LLM 意图生成草稿能力
status: active
---

## Purpose

Phase 1-3 已完成后端闭环（技能 CRUD/状态机/审批/物化、chat 读侧消费、chat 写侧自主生成草稿），但审批与人工管理只能 curl，且服务端 LLM 从意图生成草稿尚未建。本 spec 补齐前端技能管理页与 `SkillGenerationService`，让自进化循环对人类可用。

## Requirements

### Requirement: The system SHALL provide a complete skill management page
前端 SHALL 在 aicoder-web 新增技能管理页，包含全状态列表、状态过滤 tabs、详情查看（marked 渲染 SKILL.md）、手动新建/编辑、审批（通过/拒绝）、归档及「生成技能」入口。

#### Scenario: 技能列表与状态过滤
- **WHEN** 用户访问 `/skill` 路由
- **THEN** 展示技能列表表格（name/description/status tag/source tag/qualityScore/version/操作），支持按状态 tabs（全部/PENDING_APPROVAL/DRAFT/ACTIVE/REJECTED/ARCHIVED）过滤

#### Scenario: 详情查看与 markdown 渲染
- **WHEN** 用户点击某技能的"查看"按钮
- **THEN** 弹出 modal，使用 marked 库渲染 SKILL.md 内容（含 frontmatter + 步骤 + 示例），同时展示元数据

#### Scenario: 手动新建技能
- **WHEN** 用户在新建 modal 中填写 name/displayName/description/content 并提交
- **THEN** POST /api/skill 创建技能，列表刷新

#### Scenario: 审批通过
- **WHEN** 用户在 PENDING_APPROVAL 状态的技能上点击"通过"
- **THEN** PUT /api/skill/{id}/approve，技能状态变为 ACTIVE 并触发物化

#### Scenario: 审批拒绝
- **WHEN** 用户在 PENDING_APPROVAL 状态的技能上点击"拒绝"
- **THEN** PUT /api/skill/{id}/reject，技能状态变为 REJECTED

#### Scenario: 归档
- **WHEN** 用户在 ACTIVE 状态的技能上点击"归档"
- **THEN** PUT /api/skill/{id}/archive，技能状态变为 ARCHIVED

### Requirement: The system SHALL provide a server-side skill generation endpoint
skill 服务 SHALL 新增 `POST /api/skill/generate` 端点，接受意图描述，调 DeepSeek 模型按 `generate-skill` 元技能模板起草 SKILL.md，经既有 `submitDraft` 打分+闸门+版本化入库。

#### Scenario: 生成技能成功
- **WHEN** 客户端 POST `/api/skill/generate` 携带 `{intent: "代码审查清单"}` 和 `X-User-Id` 头
- **THEN** `SkillGenerationService` 调 DeepSeekChatModel 生成 SKILL.md，解析 frontmatter 取 name/description，调用 `submitDraft` 入库，返回 PENDING_APPROVAL 状态的 Skill 实体

#### Scenario: frontmatter 解析失败降级
- **WHEN** 模型返回无 frontmatter 的文本
- **THEN** name 用 intent slug 降级、description 用 intent 原文，仍调 `submitDraft`，由打分器判 REJECTED（不抛错，人工可见 REJECTED 草稿）

#### Scenario: 模型调用失败
- **WHEN** DeepSeek 调用超时或失败
- **THEN** `SkillGenerationService` 抛运行时异常，经 `GlobalExceptionHandler` 映射 HTTP 500，前端 toast "生成失败"

### Requirement: The system SHALL use the generate-skill meta-skill as generation prompt
生成 SHALL 以 `generate-skill` 元技能的 content 为 system prompt，从 DB `findByName("generate-skill")` 读取，失败则内嵌兜底模板。

#### Scenario: 元技能存在
- **WHEN** DB 中存在 name="generate-skill" 的 ACTIVE 技能
- **THEN** 以其 content 作为 system prompt 指示模型起草格式

#### Scenario: 元技能缺失
- **WHEN** DB 中无 generate-skill
- **THEN** 使用内嵌兜底模板，生成仍继续

### Requirement: The system SHALL reuse submitDraft as the sink for generated skills
生成技能 SHALL 与 chat 自主生成走同一 `submitDraft` sink（启发式打分+闸门阈值+版本化），两条路径行为一致、打分可比。

#### Scenario: 生成草稿重名版本化
- **WHEN** 生成技能 name 与已有技能重复
- **THEN** `submitDraft` 自动追加 `-vN` 后缀，版本化入库

### Requirement: The system SHALL support markdown preview with the marked library
前端 SHALL 集成 `marked` 库（~30KB 轻量 markdown 渲染）渲染 SKILL.md 内容。

#### Scenario: marked 渲染技能内容
- **WHEN** 用户在详情 modal 中查看技能
- **THEN** SKILL.md 内容（含 frontmatter + 步骤 + 示例）经 marked 渲染为格式化 HTML 展示
