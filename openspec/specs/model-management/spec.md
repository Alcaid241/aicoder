---
title: 模型管理与动态模型加载
description: 新增模型厂商配置和模型配置管理，重构 chat/rag/workflow 三个模块从数据库动态加载 ChatModel 和 EmbeddingModel，替代 YAML 硬编码
status: active
---

## Purpose

新增模型管理模块（厂商配置 + 模型配置 + 向量库配置），并重构 chat/rag/workflow 三个模块，使其从数据库动态加载 ChatModel 和 EmbeddingModel，替代 YAML 硬编码配置，实现新增模型无需改代码重启。

## Requirements

### Requirement: The system SHALL manage model providers with full CRUD
Model provider management SHALL support creating, listing, updating, and deleting AI model providers (e.g., Ollama, DeepSeek) with base URL and API key configuration.

#### Scenario: 厂商列表查询
- **WHEN** 管理员 GET `/api/admin/model/provider/list`
- **THEN** 返回所有厂商，含 name、code、base_url、api_key、enabled、sort

#### Scenario: 新增厂商
- **WHEN** 管理员 POST `/api/admin/model/provider` 携带 name、code、base_url、api_key
- **THEN** 创建 ai_model_provider 记录，code 唯一约束

#### Scenario: 删除厂商级联检查
- **WHEN** 管理员 DELETE `/api/admin/model/provider/{id}`
- **THEN** 检查是否有关联的 ai_model_config 记录，有关联则拒绝删除并提示

#### Scenario: 厂商启用/禁用
- **WHEN** 管理员更新厂商 enabled 字段
- **THEN** 对应厂商的模型在 DynamicModelRegistry 中同步可用/不可用

### Requirement: The system SHALL manage model configurations with model type support
Model config management SHALL support creating, listing, updating, and deleting model configurations with type CHAT or EMBEDDING, linked to a provider.

#### Scenario: 按类型筛选模型
- **WHEN** 管理员 GET `/api/admin/model/config/list?modelType=CHAT`
- **THEN** 返回所有 CHAT 类型的模型配置，含 display_name、model_code、所属厂商名称、enabled

#### Scenario: 新增模型配置
- **WHEN** 管理员 POST `/api/admin/model/config` 携带 providerId、displayName、modelCode、modelType
- **THEN** 创建 ai_model_config 记录，关联到指定厂商

#### Scenario: 删除模型配置
- **WHEN** 管理员 DELETE `/api/admin/model/config/{id}`
- **THEN** 删除对应模型配置记录

### Requirement: The system SHALL dynamically load ChatModel and EmbeddingModel from database
Each AI module (chat, rag, workflow) SHALL have a DynamicModelRegistry that reads enabled providers and models from database on startup and creates model instances programmatically.

#### Scenario: 启动时动态加载
- **WHEN** chat、rag、workflow 任一模块启动
- **THEN** DynamicModelRegistry @PostConstruct 读取所有启用厂商和模型，根据 provider.code 创建对应 Spring AI 客户端（OllamaApi/DeepSeekApi），再为每个 CHAT 模型创建 ChatModel、为 EMBEDDING 模型创建 EmbeddingModel

#### Scenario: 获取对话模型
- **WHEN** 业务代码调用 `dynamicModelRegistry.getChatModel("deepseek-v4-flash")`
- **THEN** 返回对应的 ChatModel 实例

#### Scenario: 获取嵌入模型
- **WHEN** 业务代码调用 `dynamicModelRegistry.getEmbeddingModel()`
- **THEN** 返回当前启用的 EmbeddingModel 实例（单例）

#### Scenario: 获取可用模型列表
- **WHEN** 前端调用模型列表接口
- **THEN** `getAvailableChatModels()` 返回所有已加载的 CHAT 模型信息

### Requirement: The system SHALL remove YAML hardcoded model configurations
Chat, rag, and workflow modules SHALL remove hardcoded model maps and spring.ai.*.chat/embedding.options from YAML, keeping only base-url and api-key for auto-configured API clients.

#### Scenario: YAML 最小化
- **WHEN** 查看各模块 application.yml
- **THEN** 仅保留 `spring.ai.ollama.base-url` 和 `spring.ai.deepseek.api-key`，移除所有 `chat.options` 和 `embedding.options` 配置

#### Scenario: 旧配置类删除
- **WHEN** 重构完成后
- **THEN** ChatModelConfig、WorkflowModelConfig、ModelService 等硬编码配置类被删除，业务代码统一注入 DynamicModelRegistry

### Requirement: The system SHALL refactor chat service to use DynamicModelRegistry
ChatService, AgentService, and ModelController in aicoder-chat SHALL inject DynamicModelRegistry instead of hardcoded model configurations.

#### Scenario: ChatService 动态模型
- **WHEN** ChatService 执行对话
- **THEN** 从 DynamicModelRegistry.getChatModel(modelCode) 获取模型，而非从硬编码 Map 查找

#### Scenario: AgentService 动态模型
- **WHEN** AgentService 执行 Agent 任务
- **THEN** 从 DynamicModelRegistry 获取 ChatModel 用于 LLM 推理和工具调用

#### Scenario: 模型列表接口动态化
- **WHEN** 前端请求可用模型列表
- **THEN** ModelController 从 DynamicModelRegistry.getAvailableChatModels() 获取，不再从硬编码列表返回

### Requirement: The system SHALL refactor rag service to use DynamicModelRegistry
RagChatService, SqlGenerationService, and DynamicVectorStoreConfig in aicoder-rag SHALL inject DynamicModelRegistry.

#### Scenario: RAG 对话动态模型
- **WHEN** RagChatService 执行 RAG 对话
- **THEN** 从 DynamicModelRegistry 获取 ChatModel

#### Scenario: SQL 生成动态模型
- **WHEN** SqlGenerationService 执行 NL2SQL
- **THEN** 从 DynamicModelRegistry 获取 ChatModel

#### Scenario: 向量存储动态 Embedding
- **WHEN** DynamicVectorStoreConfig 创建 VectorStore
- **THEN** 从 DynamicModelRegistry.getEmbeddingModel() 获取 EmbeddingModel，不再从 YAML 硬编码

### Requirement: The system SHALL refactor workflow module to use DynamicModelRegistry
WorkflowExecutor and DynamicVectorStoreConfig in aicoder-workflow SHALL inject DynamicModelRegistry.

#### Scenario: 工作流执行动态模型
- **WHEN** WorkflowExecutor 执行工作流节点
- **THEN** 从 DynamicModelRegistry 获取 ChatModel 和 EmbeddingModel

### Requirement: The system SHALL seed initial provider and model data on startup
DataInitializer in aicoder-admin SHALL auto-insert default Ollama and DeepSeek providers with preset models if they do not exist.

#### Scenario: 厂商自动初始化
- **WHEN** admin 模块启动且 OLLAMA/DEEPSEEK 厂商不存在
- **THEN** 自动插入 Ollama（base_url=http://localhost:11434）和 DeepSeek（base_url=https://api.deepseek.com）厂商记录

#### Scenario: 模型自动初始化
- **WHEN** 厂商初始化完成后
- **THEN** 自动插入 Gemma 3 4B、DeepSeek Coder、DeepSeek V4 Flash（CHAT）和 Nomic Embed Text（EMBEDDING）模型配置

### Requirement: The system SHALL provide frontend pages for provider and model configuration
The frontend SHALL provide ProviderConfigView and ModelConfigView pages under the model management menu.

#### Scenario: 厂商配置页
- **WHEN** 管理员访问 `/model/provider`
- **THEN** 表格展示所有厂商，支持新增/编辑弹窗（名称、编码、Base URL、API Key、启用状态）、删除（级联检查）

#### Scenario: 模型配置页
- **WHEN** 管理员访问 `/model/config`
- **THEN** 表格展示所有模型（名称、编码、所属厂商、类型标签 CHAT/EMBEDDING、状态），支持新增/编辑弹窗（厂商下拉、名称、编码、类型、启用）

### Requirement: The system SHALL reorganize the sidebar menu for model management
The sidebar menu SHALL add a "模型管理" top-level directory containing provider, model, and vector DB config sub-items.

#### Scenario: 菜单结构调整
- **WHEN** system DataInitializer 运行时
- **THEN** 新增一级目录"模型管理"（sort=4，在工作流之后），包含二级菜单：厂商配置、模型配置、向量库配置；移除原有的一级菜单"向量库配置"
