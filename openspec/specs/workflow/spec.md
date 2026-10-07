---
title: AI 工作流编排（aicoder-workflow 微服务）
description: Vue Flow 可视化画布编排 DAG 工作流，组合 LLM/RAG 节点，SSE 流式执行并实时查看节点状态
status: active
---

## Purpose

新增 aicoder-workflow 微服务（端口 8084），提供 AI Agent 可视化编排工作流系统。用户通过拖拽画布编排 DAG 工作流，组合 LLM 对话、RAG 检索等 AI 节点，一键执行并实时查看结果。

## Requirements

### Requirement: The system SHALL provide workflow CRUD operations
Workflow CRUD SHALL support creating, updating, deleting, listing, and retrieving workflow definitions with graph_data stored as JSON.

#### Scenario: 创建工作流
- **WHEN** 前端 POST `/api/workflow/create` 携带 name、description、graph_data
- **THEN** 创建 `ai_workflow` 记录，status 为 DRAFT，返回工作流实体

#### Scenario: 更新工作流画布
- **WHEN** 前端 PUT `/api/workflow/{id}` 提交新的 graph_data
- **THEN** 更新画布数据，updated_at 自动刷新

#### Scenario: 删除工作流
- **WHEN** 用户 DELETE `/api/workflow/{id}`
- **THEN** 删除对应工作流记录

#### Scenario: 获取工作流列表
- **WHEN** 用户 GET `/api/workflow/list`
- **THEN** 返回当前用户所有工作流，按 updated_at 倒序

### Requirement: The system SHALL execute workflows via graph engine with SSE streaming
Workflow execution SHALL parse graph_data into a StateGraph, compile and stream execution, pushing node status events via SSE.

#### Scenario: 执行线性工作流
- **WHEN** 前端 POST `/api/workflow/{id}/execute` 携带输入参数
- **THEN** 解析 graph_data 构建 StateGraph，依次执行各节点，通过 SSE 推送 `node_start`、`node_complete`、`workflow_complete` 事件

#### Scenario: 执行中记录节点状态
- **WHEN** 某节点执行失败
- **THEN** SSE 推送 `node_error` 事件，工作流状态标记为 FAILED，ai_workflow_node_execution 记录错误信息

#### Scenario: 执行历史查询
- **WHEN** 用户 GET `/api/workflow/executions/{id}` 或 GET `/api/workflow/{id}/executions`
- **THEN** 返回执行记录详情或历史列表，含各节点状态和耗时

### Requirement: The system SHALL provide LLM node for AI dialogue in workflow
LLM node SHALL take input from state, fill a prompt template, call ChatModel, and write the result to state.

#### Scenario: LLM 节点执行
- **WHEN** 工作流执行到达 LLM 节点，配置了 promptTemplate="{{input}}"、inputKey="query"、outputKey="summary"
- **THEN** 从 state 取 query 值填充模板，调用已配置的 ChatModel，结果写入 state 的 summary 键

### Requirement: The system SHALL provide RAG node for knowledge retrieval in workflow
RAG node SHALL take input from state, query VectorStore with knowledge base filter and topK, and write retrieved context to state.

#### Scenario: RAG 节点执行
- **WHEN** 工作流执行到达 RAG 节点，配置了 knowledgeBaseId、topK、inputKey、outputKey
- **THEN** 从 state 取 inputKey 值，调用 VectorStore.similaritySearch 带 knowledgeBaseId 过滤，拼接检索结果为上下文字符串写入 outputKey

### Requirement: The system SHALL provide a Vue Flow canvas editor for visual workflow design
The frontend SHALL provide a drag-and-drop canvas editor using Vue Flow with node panel, config panel, and execution panel.

#### Scenario: 拖拽节点到画布
- **WHEN** 用户从左侧节点面板拖拽 LLM/RAG/开始/结束 节点到画布
- **THEN** 画布上显示对应节点，可拖拽调整位置

#### Scenario: 连线创建边
- **WHEN** 用户从节点输出端口拖线到另一节点输入端口
- **THEN** 创建连线（edge），graph_data.edges 中记录 source 和 target

#### Scenario: 配置节点属性
- **WHEN** 用户点击画布上的节点
- **THEN** 右侧配置面板显示该节点类型的配置表单（模型选择、Prompt 模板、输入输出键等）

#### Scenario: 运行工作流
- **WHEN** 用户在底部执行面板输入参数并点击运行
- **THEN** SSE 实时更新节点颜色（灰=PENDING → 蓝=RUNNING → 绿=COMPLETED / 红=FAILED），底部显示最终输出

### Requirement: The system SHALL persist workflow data in MySQL
Three new tables SHALL store workflow definitions, execution records, and node execution details in the test_ai database.

#### Scenario: 工作流定义持久化
- **WHEN** 前端保存工作流
- **THEN** ai_workflow 表存储 name、description、graph_data（JSON）、status、user_id

#### Scenario: 执行记录持久化
- **WHEN** 工作流执行开始和结束
- **THEN** ai_workflow_execution 表记录 status（RUNNING/COMPLETED/FAILED/CANCELLED）、input/output、error_message、时间戳

#### Scenario: 节点执行记录
- **WHEN** 每个节点执行
- **THEN** ai_workflow_node_execution 表记录 node_id、node_type（LLM/RAG/NL2SQL/CONDITION/TOOL）、status、input_data/output_data、error_message、耗时

### Requirement: The system SHALL route /api/workflow/** via Gateway
Gateway SHALL forward /api/workflow/** requests to lb://aicoder-workflow.

#### Scenario: 网关路由转发
- **WHEN** 前端访问 `/api/workflow/**`
- **THEN** Gateway 转发到 aicoder-workflow:8084，请求经 JWT 鉴权后携带 X-User-Id 头
