---
title: 模型 Function-Calling 能力标记
description: ai_model_config 增加 support_tools 字段，ChatService 按模型实际能力决定是否挂工具，替换 instanceOf OllamaChatModel 硬编码判断
status: active
---

## Purpose

当前 ChatService 用 `instanceof OllamaChatModel` 一刀切判断模型是否支持 function-calling，但 Ollama 0.30.11 + Spring AI 1.1.2 已完整支持 tools。实际能力取决于具体模型（gemma3/glm-4.7-flash 支持，deepseek-coder:6.7b 不支持）。本变更在模型配置层面增加 `support_tools` 标记，让管理员在配置模型时显式指定是否支持 tools。

## Requirements

### Requirement: The system SHALL store tool support flag in model configuration
`ai_model_config` table SHALL have a `support_tools` column (TINYINT, default 0, nullable=false) to indicate whether the model supports function-calling (tools).

#### Scenario: 新建模型时设置 support_tools
- **WHEN** 管理员 POST `/api/admin/model/config` 创建模型配置，携带 `supportTools: 1`
- **THEN** ai_model_config 记录中 support_tools=1，表示该模型支持 function-calling

#### Scenario: 修改模型 support_tools
- **WHEN** 管理员 PUT `/api/admin/model/config/{id}` 更新模型配置，修改 supportTools 字段
- **THEN** ai_model_config 记录中 support_tools 更新为新值

#### Scenario: 默认值为 0
- **WHEN** 创建模型配置时不提供 supportTools 字段
- **THEN** 默认 support_tools=0，模型不挂工具

### Requirement: The system SHALL update DataInitializer to seed tool support flag
`ModelDataInitializer` SHALL mark DeepSeek models with `support_tools=1` and Ollama models with `support_tools=0` (admin can override).

#### Scenario: DeepSeek 模型自动标记
- **WHEN** admin 模块启动并初始化数据
- **THEN** deepseek-v4-flash 和 deepseek-v4-pro 的 support_tools 设为 1

#### Scenario: Ollama 模型默认不标记
- **WHEN** admin 模块启动并初始化数据
- **THEN** 所有 Ollama 模型的 support_tools 默认为 0

### Requirement: The system SHALL use model tool flag in ChatService instead of provider check
ChatService.buildSkillClient() SHALL read the model's `support_tools` flag from ModelConfig, rather than `instanceof OllamaChatModel`, to decide whether to register tools.

#### Scenario: 支持 tools 的模型挂工具
- **WHEN** ChatService 为 support_tools=1 的模型创建 ChatClient
- **THEN** builder.defaultTools(readSkillTool, submitSkillDraftTool) 被调用

#### Scenario: 不支持 tools 的模型不挂工具
- **WHEN** ChatService 为 support_tools=0 的模型创建 ChatClient
- **THEN** 仅挂 advisor，不挂 defaultTools

#### Scenario: 不再依赖 provider 类型判断
- **WHEN** ChatService.buildSkillClient() 执行
- **THEN** 代码中不出现 `instanceof OllamaChatModel`
