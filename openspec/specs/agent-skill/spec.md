---
title: Agent Skill 自进化体系
description: 建立智能对话匹配、缺失则生成、验证固化、复用迭代的技能自增长闭环，使 AI 能力以标准化可复用的技能模块形式沉淀。
status: active
---

## Purpose

在 chat / rag / workflow 微服务之上建立技能工程体系，通过渐进式披露降低 Token 消耗，通过元技能自进化形成「检索 → 生成 → 验证 → 固化 → 复用」闭环，避免 AI 能力写死在提示词或硬编码工具里。

## Requirements

### Requirement: The system SHALL maintain a dual-plane architecture separating skill management from skill runtime

The system SHALL separate skill management (aicoder-skill :8086) from skill runtime (chat/rag/workflow). 管理面负责技能全生命周期管理（CRUD、生成编排、质量打分、审批、物化），运行面各自实例化原生 SkillRegistry 指向共享目录，只读消费已 ACTIVE 的技能。

#### Scenario: 管理面物化技能到共享目录
- **WHEN** 管理员审批通过一个 PENDING_APPROVAL 状态的技能
- **THEN** `SkillRegistrySyncService` 将该技能的 SKILL.md 写入 Nacos 共享配置 `skill.directory` 指向的目录，运行面下一轮对话即可感知新技能

#### Scenario: 运行面只读消费技能
- **WHEN** chat 服务启动并配置 FileSystemSkillRegistry 指向共享目录
- **THEN** 运行面能列出并加载 ACTIVE 技能，但不会直接写数据库或修改技能状态

### Requirement: The system SHALL enforce a skill lifecycle state machine with five states

The system SHALL enforce DRAFT / PENDING_APPROVAL / ACTIVE / REJECTED / ARCHIVED states. 技能生命周期状态机定义五态流转，只有 ACTIVE 技能被物化到共享目录进入运行面，其余状态仅存储于数据库。

#### Scenario: 新技能从草稿到激活
- **WHEN** 一个 DRAFT 技能通过自动质量打分且分数达到阈值
- **THEN** 技能自动转为 PENDING_APPROVAL 状态等待人工审批

#### Scenario: 审批通过激活技能
- **WHEN** 管理员对 PENDING_APPROVAL 技能执行 approve 操作
- **THEN** 技能状态转为 ACTIVE，SKILL.md 物化到共享目录，运行面可发现该技能

#### Scenario: 审批拒绝
- **WHEN** 管理员对 PENDING_APPROVAL 技能执行 reject 操作
- **THEN** 技能状态转为 REJECTED，不物化到共享目录

### Requirement: The system SHALL support progressive disclosure of skill content

The system SHALL inject only skill name and one-line description into system prompts. 智能体初始只感知精简技能目录（名称 + 一句话描述），匹配时再通过 `read_skill` 工具加载完整 SKILL.md 正文，降低 Token 消耗并避免工具过载。

#### Scenario: 模型按需加载技能
- **WHEN** 模型判断用户提问匹配某个 ACTIVE 技能
- **THEN** 模型调用 read_skill 工具按名称加载完整技能内容，而非在初始提示中包含所有技能的全文

#### Scenario: 技能目录注入系统提示
- **WHEN** 对话开始时 SkillsAgentHook 或 SkillPromptAugmentAdvisor 执行
- **THEN** 所有 ACTIVE 技能的名称和一句话描述被注入系统提示，但完整正文不注入

### Requirement: The system SHALL implement a meta-skill self-evolution closed loop

The system SHALL enable models to generate new skills when no match is found. 将"生成新技能"封装为元技能 `generate-skill`，形成「目录匹配不成功 → 模型加载元技能起草 → 调用 submit_skill_draft 提交 → 进审批队列 → 审批通过物化 → 下一轮可复用」的自增长闭环。

#### Scenario: 模型自主生成新技能
- **WHEN** 模型判定技能目录中无匹配技能且当前任务为可复用的重复性模式
- **THEN** 模型加载元技能 generate-skill，按其指令起草新 SKILL.md 并调用 submit_skill_draft 工具提交

#### Scenario: 闭环完成后技能复用
- **WHEN** 新技能经审批通过并物化到共享目录后用户再次提出类似问题
- **THEN** 模型在下一轮对话中可直接匹配并加载该新技能，无需重新生成

### Requirement: The system SHALL store skill data with DB as the single source of truth

The system SHALL treat the ai_skill table as the sole source of truth for skill metadata. 技能元数据以 `ai_skill` 表为唯一真相源，字段涵盖 name（kebab-case 唯一标识）、display_name、description、content（完整 SKILL.md 正文）、version、status、source（MANUAL / AUTO_GENERATED）、quality_score、trial_result 等；文件系统的 SKILL.md 是物化产物而非真相源。

#### Scenario: 技能版本迭代
- **WHEN** 对已有技能进行编辑迭代
- **THEN** 新版本通过 parent_skill_id 关联原版本，version 递增，形成版本血缘链

#### Scenario: 查询技能完整信息
- **WHEN** 通过 REST API 查询技能详情
- **THEN** 返回数据库中的完整字段包括质量分、试运行结果、审批信息等，文件系统仅作为运行面消费的物化产物

### Requirement: The system SHALL support both auto-generated and manual skill creation

The system SHALL route both AUTO_GENERATED and MANUAL skill sources through the same state machine. 技能来源区分 `AUTO_GENERATED`（元技能产出）与 `MANUAL`（管理页面手工创建），两者走同一状态机和审批流程。

#### Scenario: 管理页面手工创建技能
- **WHEN** 用户在技能管理页填写名称、描述、完整 SKILL.md 正文并提交
- **THEN** 技能以 MANUAL 来源创建为 DRAFT 状态，可进入审批流程

#### Scenario: 元技能自动生成技能
- **WHEN** 模型在对话中判定无匹配技能并自主起草提交
- **THEN** 技能以 AUTO_GENERATED 来源创建为 DRAFT 状态，触发自动质量打分
