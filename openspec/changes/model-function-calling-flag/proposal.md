## Why

ChatService 当前用 `instanceof OllamaChatModel` 一刀切判断模型是否支持 function-calling，但 Ollama 0.30.11 和 Spring AI 1.1.2 的 `OllamaChatModel` 都已完整支持 tools。实际能否 function-calling 取决于**具体模型本身**（新模型如 gemma3/glm-4.7-flash 支持，老模型如 deepseek-coder:6.7b 不支持），而非 provider。这导致支持 tools 的 Ollama 模型被错误降级，技能工具挂不上。

## What Changes

- `ai_model_config` 表新增 `support_tools` 字段（TINYINT，默认 0），标记该模型是否支持 function-calling
- aicoder-core 的 `ModelConfig` 实体新增对应字段
- aicoder-admin 模型配置 CRUD 接口支持 `support_tools` 字段
- 前端模型管理表单增加「支持 Function Calling」开关
- ChatService 去掉 `instanceof OllamaChatModel` 判断，改为读取 `ModelConfig.supportTools` 决定是否挂工具
- DataInitializer 初始化时把 DeepSeek 模型标记为 `support_tools=1`，Ollama 模型默认 0（管理员按实际模型配置）
- 现有 `ai_model_config` 表中已有的 deepseek-v4-flash/deepseek-v4-pro 需 migration 设 `support_tools=1`

## Capabilities

### New Capabilities
- `model-tool-capability`: 模型配置增加 function-calling 支持标记，ChatService 按模型实际能力决定是否挂工具

### Modified Capabilities
- `model-management`: `ai_model_config` 表结构变更（新增 `support_tools` 字段），admin 模型配置 CRUD 接口和前端表单新增该字段

## Impact

| 模块 | 改动 |
|------|------|
| aicoder-core | `ModelConfig` 实体加 `supportTools` 字段 |
| aicoder-admin | `ModelController` CRUD 支持新字段；`ModelDataInitializer` 设默认值 |
| aicoder-chat | `ChatService.buildSkillClient()` 改读 `ModelConfig.supportTools`，去掉 `instanceof OllamaChatModel` |
| 前端 aicoder-web | 模型配置表单加 switch 组件 |
| 数据库 | `ai_model_config` 加列 + 存量数据 migration |
