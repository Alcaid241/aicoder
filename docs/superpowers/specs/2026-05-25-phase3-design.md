# 工作流第三期设计 — 补齐遗留 + 工作流增强 + Agent 模式

## 概述

第三期分两个批次交付：

- **批次 A**：补齐遗留问题 + 首页仪表盘 + 工作流模板 + 子工作流/循环节点
- **批次 B**：AI Agent 模式（依赖批次 A 的工作流基础设施）

---

## 批次 A

### A1. 补齐遗留问题

#### A1.1 RAG 对话前端 API 封装

**现状**：`rag.ts` 文件存放的是 `vectorDbApi`（向量库配置），缺少 RAG 对话的流式调用封装。后端 `POST /api/rag/chat/stream` 已存在但前端无法正确调用。

**改动**：

- 将 `rag.ts` 重命名为 `vectorDb.ts`（内容不变）
- 新建 `rag.ts`，封装 RAG 对话流式 API：
  - `streamRagChat(conversationId, knowledgeBaseId, message, onEvent)` — 调用 `POST /api/rag/chat/stream`
  - 使用统一的 `streamRequest` 方法（见 A1.3）
- 修改 `RagChatView.vue` 使用新封装的 API

#### A1.2 用户资料编辑

**现状**：前端有 `/profile` 页面，后端只有 `GET /api/admin/user/info`，缺少更新接口。

**后端改动**（aicoder-admin）：

- `AuthController` 新增 `PUT /api/admin/user/info`，接收 `nickname`、`email`、`avatar` 字段
- `AuthService` 新增 `updateUserInfo(Long userId, String nickname, String email, String avatar)` 方法

**前端改动**：

- `auth.ts` 新增 `updateUserInfo(data)` 方法
- `ProfileView.vue` 添加编辑表单（昵称、邮箱、头像 URL）
- 保存成功后更新 `userStore` 中的用户信息

#### A1.3 流式 API 统一封装

**现状**：Chat 和 Workflow 各自用不同方式处理 SSE，代码重复。

**改动**：

- `api/request.ts` 新增 `streamRequest(url, body, onEvent, onError, onComplete)` 通用方法
  - 封装 fetch + ReadableStream + SSE 事件解析
  - `onEvent(eventType, data)` 回调统一处理各类 SSE 事件
- `chat.ts`、`rag.ts`、`workflow.ts` 中的流式调用统一使用此方法
- 删除各组件中重复的 SSE 处理代码

---

### A2. 首页仪表盘

#### A2.1 后端统计接口

各微服务新增统计端点，admin 聚合调用：

**aicoder-chat**：
- `GET /api/chat/stats` — 返回 `{ conversationCount: number }`
- 从 `ai_conversation` 表按 `user_id` 统计

**aicoder-rag**：
- `GET /api/rag/stats` — 返回 `{ knowledgeBaseCount: number }`
- 从 `ai_knowledge_base` 表按 `user_id` 统计

**aicoder-workflow**：
- `GET /api/workflow/stats` — 返回 `{ workflowCount: number, executionCount: number }`
- 从 `ai_workflow` 和 `ai_workflow_execution` 表按 `user_id` 统计

**aicoder-admin**：
- `GET /api/admin/dashboard/stats` — 聚合调用以上三个接口，返回合并结果
- 使用 WebClient + Nacos 服务发现直接调用各微服务（不经过 Gateway，避免回环）
- 手动传递当前用户的 `X-User-Id` 请求头给下游服务

#### A2.2 前端仪表盘页面

重写 `HomeView.vue`：

- **统计卡片**（4 个）：对话数、知识库数、工作流数、执行次数
- **最近活动**：最近 5 条对话 + 最近 5 个工作流执行记录
- **快捷入口**：新建对话、新建知识库、新建工作流按钮

新增 API 封装：
- `dashboard.ts`（或放在 `auth.ts` 中）— `GET /admin/dashboard/stats`

---

### A3. 工作流模板

#### A3.1 数据模型

```sql
CREATE TABLE IF NOT EXISTS ai_workflow_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) COMMENT '描述',
    category VARCHAR(50) COMMENT '分类: RAG/SQL/CHAT/AGENT',
    graph_data JSON NOT NULL COMMENT '画布数据（与 ai_workflow.graph_data 同结构）',
    is_system TINYINT DEFAULT 0 COMMENT '1-系统预置 0-用户自建',
    user_id BIGINT COMMENT '创建人（系统模板为 NULL）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流模板表';
```

#### A3.2 后端（aicoder-workflow）

**新建文件**：

- `model/entity/WorkflowTemplate.java` — 实体类
- `repository/WorkflowTemplateRepository.java` — JPA Repository
- `service/WorkflowTemplateService.java` — 模板管理服务
- `controller/WorkflowTemplateController.java` — REST 接口

**API 端点**：

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/workflow/templates` | 列出模板（系统模板 + 用户自建） |
| POST | `/api/workflow/templates` | 创建模板（可从现有工作流创建） |
| POST | `/api/workflow/templates/{id}/clone` | 从模板创建新工作流 |
| DELETE | `/api/workflow/templates/{id}` | 删除模板（仅自己的） |

**预置系统模板**（应用启动时自动插入）：

1. **RAG 问答流程**：开始 → RAG 检索 → LLM 总结 → 结束
2. **NL2SQL 数据分析**：开始 → NL2SQL → LLM 解读 → 结束
3. **多源检索**：开始 → RAG 检索 → 条件分支 → (有结果) → LLM → 结束 / (无结果) → NL2SQL → 结束

#### A3.3 前端

- `workflow.ts` 新增模板相关 API 方法
- `WorkflowListView.vue` 新增「从模板创建」按钮
- 新增模板选择弹窗组件 `TemplateSelectModal.vue`（或用 Dialog 实现）
  - 分类展示系统模板和用户模板
  - 点击模板显示预览信息
  - 确认后克隆为新工作流并跳转编辑器

---

### A4. 子工作流 / 循环节点

#### A4.1 子工作流节点（SubWorkflowNode）

**后端**：

- 新建 `node/SubWorkflowNode.java`，实现 `NodeAction`
- 配置字段：`workflowId`（目标工作流 ID）、`inputKey`、`outputKey`
- 执行时通过 `WorkflowExecutionService` 执行目标工作流的 graph_data
- 子工作流的最终输出作为当前节点的输出
- `WorkflowNodeFactory` 添加 `subworkflow` 分支

**前端**：

- 新建 `SubWorkflowNode.vue` 画布组件，显示目标工作流名称
- `ConfigPanel.vue` 添加子工作流配置表单（下拉选择已有工作流）
- `FlowCanvas.vue` 和 `NodePanel.vue` 注册 `subworkflow` 类型

#### A4.2 循环节点（LoopNode）

**后端**：

- 新建 `node/LoopNode.java`，实现 `NodeAction`
- 配置字段：
  - `maxIterations`（int，最大循环次数，默认 5）
  - `exitCondition`（String，简单字符串匹配：当 `state.value(inputKey)` 包含该字符串时退出，如 `"完成"`）
  - `inputKey`、`outputKey`
- 执行逻辑（在 `apply()` 内部循环）：
  1. 从 state 取 inputKey 的值
  2. 检查是否包含 exitCondition 字符串，包含则退出循环
  3. 检查是否达到 maxIterations，达到则退出
  4. 否则透传 state，继续等待下一轮输入
  5. 退出后将最终值写入 outputKey
- LoopNode 与图中的回路边配合使用：从 LoopNode 的 source 拉一条边回到循环体的起始节点
- `WorkflowNodeFactory` 添加 `loop` 分支

**前端**：

- 新建 `LoopNode.vue` 画布组件，显示循环图标和最大迭代次数
- `ConfigPanel.vue` 添加循环配置表单（最大次数、退出条件）
- `FlowCanvas.vue` 和 `NodePanel.vue` 注册 `loop` 类型

#### A4.3 WorkflowNodeConfig 类型扩展

```typescript
// 新增字段
export interface WorkflowNodeConfig {
  // ... 现有字段
  // SubWorkflow
  workflowId?: string
  // Loop
  maxIterations?: number
  exitCondition?: string
}
```

---

## 批次 B：AI Agent 模式

### B1. 后端 Agent 服务（aicoder-chat 扩展）

#### B1.1 核心架构

基于 Spring AI 的 Tool Calling 机制实现 Agent：

- 用户消息 → LLM 推理 → 判断是否需要调用工具 → 调用工具 → 观察结果 → 继续推理 → 输出答案
- 复用 `ai_conversation` 表，`type = 'AGENT'`
- 流式输出，包含推理过程和工具调用

#### B1.2 新建文件

- `controller/AgentController.java` — Agent 对话接口
- `service/AgentService.java` — Agent 核心服务
- `tool/KnowledgeSearchTool.java` — 知识库检索工具
- `tool/SqlQueryTool.java` — SQL 查询工具
- `tool/HttpRequestTool.java` — HTTP 请求工具

#### B1.3 API 端点

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/chat/agent/stream` | SSE 流式 Agent 对话 |

请求体：
```json
{
  "message": "帮我分析一下最近一个月的销售数据",
  "conversationId": 123,
  "modelId": "deepseek-v4-flash"
}
```

#### B1.4 SSE 事件类型

| 事件类型 | 数据 | 说明 |
|---------|------|------|
| `agent_think` | `{ "content": "我需要先搜索..." }` | Agent 思考过程 |
| `tool_call` | `{ "tool": "knowledge_search", "args": {...} }` | 工具调用开始 |
| `tool_result` | `{ "tool": "knowledge_search", "result": "..." }` | 工具返回结果 |
| `agent_answer` | `{ "content": "根据分析..." }` | 最终答案（流式分片） |
| `done` | `{}` | 完成 |

#### B1.5 工具定义

**KnowledgeSearchTool**：
- 通过 WebClient + Nacos 直接调用 aicoder-rag 的向量检索能力（不经过 Gateway）
- 入参：`query`（查询文本）、`knowledgeBaseId`（可选，默认搜索所有）
- 返回：相关文档片段列表

**SqlQueryTool**：
- 通过 WebClient + Nacos 直接调用 aicoder-rag 的 NL2SQL 能力
- 入参：`question`（自然语言问题）、`databaseName`（可选）
- 返回：SQL 执行结果（JSON）

**HttpRequestTool**：
- 入参：`url`、`method`、`body`（可选）
- 返回：HTTP 响应体

### B2. 前端 Agent 界面

#### B2.1 改动范围

- `chatStore` 扩展：新增 Agent 相关状态（思考步骤列表、工具调用记录）
- `ChatView.vue` 扩展：
  - 新建会话时可选择「Agent 模式」
  - Agent 消息渲染：思考过程折叠展示、工具调用显示为卡片
  - 普通对话和 Agent 模式共用同一个页面，通过 `type` 区分展示

#### B2.2 消息渲染格式

Agent 消息展示为时间线形式：

```
🤔 思考: 我需要先搜索销售相关的数据...
  ↓
🔍 调用工具: knowledge_search("销售数据")
  → 返回 3 条相关文档
  ↓
🤔 思考: 找到了销售数据表，现在需要查询具体数据...
  ↓
💾 调用工具: sql_query("查询最近一个月的销售总额")
  → 返回查询结果: 25 条记录
  ↓
📝 最终回答: 根据数据分析，最近一个月的销售总额为...
```

---

## 技术约束

- Java 17，Spring Boot 3.5.13
- Spring AI 1.1.2 的 Tool Calling 机制
- 前端 Vue 3.5 + TypeScript
- 微服务间通过 Gateway 路由调用
- 所有新增接口需要通过 Gateway JWT 鉴权

## 不在范围内

- 定时调度工作流
- 工作流版本管理
- 对话分享/导出
- 多租户权限
- Docker 化和 CI/CD
