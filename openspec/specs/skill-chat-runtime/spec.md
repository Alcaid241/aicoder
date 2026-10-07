---
title: Agent Skill Phase 2 — Chat 运行面读侧接入
description: 让 aicoder-chat 消费共享技能目录，通过 SkillPromptAugmentAdvisor 注入技能目录并自建 read_skill 工具实现渐进式披露的读侧。
status: active
---

## Purpose

Phase 1 管理面已完成技能 CRUD 与物化，本阶段让 aicoder-chat (:8082) 在对话中消费这些技能：将 ACTIVE 技能目录注入系统提示，模型按需 `read_skill(name)` 加载完整 SKILL.md 套用，实现渐进式披露的读侧。范围只做读侧，写回与自进化闭环划为 Phase 3。

## Requirements

### Requirement: The system SHALL inject active skill directory into chat system prompts via SkillPromptAugmentAdvisor

The system SHALL use graph-core's native SkillPromptAugmentAdvisor to append skill names and descriptions into system prompts before each conversation turn. 实现渐进式披露的目录层。

#### Scenario: 对话开始时技能目录被注入
- **WHEN** 用户通过 chat 发送消息且共享目录中存在 ACTIVE 技能
- **THEN** `SkillPromptAugmentAdvisor.before()` 在系统提示中追加所有 ACTIVE 技能的名称和一句话描述，但完整正文不注入

#### Scenario: 共享目录为空时不影响对话
- **WHEN** 共享技能目录中无 ACTIVE 技能
- **THEN** `SkillPromptAugmentAdvisor` 不追加额外内容，对话正常进行

### Requirement: The system SHALL provide a read_skill tool for on-demand skill content loading

The system SHALL provide a ReadSkillTool that wraps FileSystemSkillRegistry.readSkillContent(name) for model-triggered skill loading. 模型判定匹配某技能时调用该工具加载完整 SKILL.md 正文。

#### Scenario: 命中技能加载完整内容
- **WHEN** 模型调用 `read_skill("greeting-skill")` 且该技能存在于共享目录
- **THEN** `ReadSkillTool` 返回该技能的完整 SKILL.md 正文，模型据此作答

#### Scenario: 缺失技能返回友好提示
- **WHEN** 模型调用 `read_skill("nonexistent")` 但共享目录中不存在该技能
- **THEN** `ReadSkillTool` 返回友好提示信息而非抛异常，不中断对话

#### Scenario: 路径穿越名被拒绝
- **WHEN** 模型尝试调用 `read_skill("../etc/passwd")` 等包含路径穿越的非法技能名
- **THEN** `ReadSkillTool` 拒绝该请求并返回提示，防止目录穿越攻击

### Requirement: The system SHALL use ChatClient with advisor and tool integration for skill-aware conversations

The system SHALL refactor ChatService to use Spring AI ChatClient with SkillPromptAugmentAdvisor and ReadSkillTool. 保留既有系统提示、会话历史装配与 SSE 事件结构。

#### Scenario: 非流式对话含技能匹配
- **WHEN** 用户发送非流式请求且模型匹配某技能
- **THEN** ChatClient 链路的 advisor 注入目录 → 模型调 read_skill 加载技能内容 → 返回套用技能后的完整回答

#### Scenario: 流式对话保持 SSE 契约
- **WHEN** 用户发送流式请求
- **THEN** `chatClient...stream()` 产出 SSE 事件流，保持与前端现有契约一致，同时 advisor 仍注入技能目录

#### Scenario: 模型不支持工具调用时目录注入仍生效
- **WHEN** 当前模型不支持 function-calling
- **THEN** `SkillPromptAugmentAdvisor` 仍将技能目录注入系统提示（advisor 与工具能力无关），模型可感知技能目录但无法触发 read_skill

### Requirement: The system SHALL read skill directory from the same Nacos shared configuration as the skill management service

The system SHALL import nacos:aicoder-shared.yml via spring.config.import so chat reads the same skill.directory as the skill service. 确保 chat 与 skill 服务指向同一绝对路径。

#### Scenario: chat 与 skill 服务共享目录
- **WHEN** chat 服务启动且 Nacos 可用
- **THEN** chat 的 `SkillProperties` 解析出与 skill 服务相同的绝对路径，`FileSystemSkillRegistry` 读取 skill 服务物化的 SKILL.md 文件

#### Scenario: Nacos 不可用时 chat 回退本地配置
- **WHEN** Nacos 不可用
- **THEN** `optional:` 前缀保证 chat 服务仍可启动，使用本地 `application.yml` 的回退值

### Requirement: The system SHALL resolve skill directory to absolute path with startup log visibility

The system SHALL resolve relative paths to absolute and log at WARN level with source indicator. chat 服务的 `SkillProperties` 复刻 skill 服务的绝对路径解析逻辑，防止 cwd 混淆。

#### Scenario: 启动时打印技能目录
- **WHEN** chat 服务启动
- **THEN** 日志输出 `[skill] directory resolved to: <绝对路径> (source: nacos|env|default)`，使管理员可确认技能目录来源与路径

### Requirement: The system SHALL scope Phase 2 to read-only and exclude write-back

The system SHALL only cover read-side in Phase 2: advisor directory injection and read_skill on-demand loading. submit_skill_draft 写回、元技能生成、质量打分、审批队列前端均划为 Phase 3。

#### Scenario: 模型发现无匹配技能时不触发生成
- **WHEN** 模型判定技能目录中无匹配技能
- **THEN** 模型直接作答，不尝试起草新技能或调用 submit_skill_draft（Phase 2 不注册该工具）
