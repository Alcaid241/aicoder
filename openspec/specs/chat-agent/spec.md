---
title: 对话 Agent 模式与工作流增强（Phase 3）
description: 补齐 RAG 前端 API、用户资料编辑、统一 SSE 流式封装、首页仪表盘、工作流模板、子工作流/循环节点、AI Agent 模式
status: active
---

## Purpose

第三期分两个批次：批次 A 补齐遗留问题（RAG 对话前端、用户资料编辑、流式 API 统一）、首页仪表盘、工作流模板、子工作流/循环节点；批次 B 基于 Spring AI Tool Calling 实现 AI Agent 模式，包含思考过程、工具调用和流式输出。

## Requirements

### Requirement: The system SHALL encapsulate RAG chat streaming API on the frontend
The frontend SHALL provide a dedicated rag.ts API module for RAG chat streaming calls using the unified streamRequest method.

#### Scenario: RAG 流式对话
- **WHEN** 前端调用 `streamRagChat(conversationId, knowledgeBaseId, message, onEvent)`
- **THEN** 通过统一 streamRequest 方法 POST `/api/rag/chat/stream`，解析 SSE 事件并回调 onEvent

#### Scenario: RAG API 模块独立
- **WHEN** rag.ts 重命名为 vectorDb.ts 后新建 rag.ts
- **THEN** RagChatView.vue 使用新 rag.ts 的流式封装，不再通过错误路径调用

### Requirement: The system SHALL support user profile editing
The backend SHALL provide PUT /api/admin/user/info for updating nickname, email, and avatar.

#### Scenario: 更新用户资料
- **WHEN** 前端 PUT `/api/admin/user/info` 携带 nickname、email、avatar
- **THEN** AuthService 更新对应用户字段，前端 userStore 同步刷新

#### Scenario: Profile 页面编辑
- **WHEN** 用户在 ProfileView.vue 编辑表单并保存
- **THEN** 调用 updateUserInfo API，保存成功后更新全局用户状态

### Requirement: The system SHALL unify SSE streaming with a common streamRequest method
The frontend SHALL provide a single streamRequest utility in request.ts for all SSE streaming calls across chat, rag, and workflow modules.

#### Scenario: 统一流式调用
- **WHEN** chat.ts、rag.ts、workflow.ts 需要流式调用
- **THEN** 全部使用 `streamRequest(url, body, onEvent, onError, onComplete)` 方法，各组件删除重复的 SSE 处理代码

#### Scenario: SSE 事件解析
- **WHEN** 服务端推送 SSE 事件流
- **THEN** streamRequest 通过 fetch + ReadableStream 解析，按 eventType 回调 onEvent

### Requirement: The system SHALL provide a dashboard with aggregated statistics
The admin module SHALL aggregate statistics from chat, rag, and workflow services and display them on the home page.

#### Scenario: 仪表盘聚合统计
- **WHEN** 用户访问首页，前端 GET `/api/admin/dashboard/stats`
- **THEN** admin 通过 WebClient + Nacos 直接调用 chat/rag/workflow 的统计端点，返回对话数、知识库数、工作流数、执行次数

#### Scenario: 各服务统计端点
- **WHEN** admin 调用各服务的 stats 端点
- **THEN** chat 返回 conversationCount，rag 返回 knowledgeBaseCount，workflow 返回 workflowCount 和 executionCount，均按 user_id 过滤

#### Scenario: 仪表盘页面渲染
- **WHEN** HomeView.vue 收到聚合统计数据
- **THEN** 展示 4 个统计卡片、最近活动列表、快捷入口按钮

### Requirement: The system SHALL support workflow templates
Workflow templates SHALL allow users to create workflows from predefined or user-defined templates.

#### Scenario: 从模板创建工作流
- **WHEN** 用户点击「从模板创建」并选择模板
- **THEN** POST `/api/workflow/templates/{id}/clone` 克隆模板的 graph_data 为新工作流，跳转编辑器

#### Scenario: 系统预置模板
- **WHEN** aicoder-workflow 启动时
- **THEN** 自动插入 RAG 问答流程、NL2SQL 数据分析、多源检索三个系统模板到 ai_workflow_template 表

#### Scenario: 用户自建模板
- **WHEN** 用户从现有工作流 POST `/api/workflow/templates` 创建模板
- **THEN** 模板保存该工作流的 graph_data，可被自己和他人克隆使用

### Requirement: The system SHALL support sub-workflow and loop nodes
SubWorkflow node SHALL execute another workflow's graph_data as a sub-routine; Loop node SHALL iterate based on exit condition and max iterations.

#### Scenario: 子工作流节点执行
- **WHEN** 工作流执行到达 SubWorkflow 节点，配置了 workflowId
- **THEN** 通过 WorkflowExecutionService 执行目标工作流的 graph_data，子工作流最终输出作为当前节点输出

#### Scenario: 循环节点执行
- **WHEN** 工作流执行到达 Loop 节点，配置了 maxIterations 和 exitCondition
- **THEN** 每轮检查 state 值是否包含 exitCondition 字符串或达到 maxIterations，退出后将最终值写入 outputKey

#### Scenario: 子工作流前端配置
- **WHEN** 用户在配置面板选择 SubWorkflow 节点
- **THEN** 下拉选择已有工作流作为子工作流目标

### Requirement: The system SHALL provide AI Agent mode with tool calling
Agent mode SHALL use Spring AI Tool Calling to enable multi-step reasoning with tool invocations, streaming think/tool_call/tool_result/answer events via SSE.

#### Scenario: Agent 流式对话
- **WHEN** 前端 POST `/api/chat/agent/stream` 携带 message、conversationId、modelId
- **THEN** AgentService 调用 LLM，LLM 判断是否需要工具，按序流式推送 `agent_think`、`tool_call`、`tool_result`、`agent_answer`、`done` 事件

#### Scenario: 知识库检索工具
- **WHEN** Agent 需要检索知识库
- **THEN** KnowledgeSearchTool 通过 WebClient + Nacos 直接调用 aicoder-rag 向量检索，返回相关文档片段

#### Scenario: SQL 查询工具
- **WHEN** Agent 需要查询数据库
- **THEN** SqlQueryTool 通过 WebClient + Nacos 直接调用 aicoder-rag NL2SQL 能力，返回 SQL 执行结果

#### Scenario: Agent 消息前端渲染
- **WHEN** 前端收到 Agent SSE 事件流
- **THEN** ChatView.vue 以时间线形式展示：思考过程折叠、工具调用显示为卡片、最终回答正常渲染；普通对话和 Agent 模式通过 type 区分

### Requirement: The system SHALL reuse ai_conversation table for Agent conversations
Agent conversations SHALL be stored in ai_conversation with type='AGENT', reusing the existing conversation infrastructure.

#### Scenario: Agent 会话创建
- **WHEN** 用户选择 Agent 模式新建会话
- **THEN** 创建 ai_conversation 记录 type=AGENT，前端通过 chatStore 扩展 Agent 相关状态
