# aicoder-workflow 操作手册

> 可视化 AI 工作流编排服务。本文覆盖 aicoder-workflow（`:8084`）的全部功能：工作流 CRUD、9 种节点、graph-core 执行引擎、模板、前端可视化编辑器、配置与排障。
>
> **关联设计**：[workflow-design](superpowers/specs/2026-05-22-workflow-design.md)。

---

## 1. 概述

`aicoder-workflow` 让用户用**拖拽节点、连线**的方式编排 AI 工作流（DAG），把 LLM 调用、RAG 检索、NL2SQL、条件分支、HTTP 工具、循环、子工作流串联成可复用、可执行、可观测的流程。工作流以图（JSON）存 DB，执行时由 Spring AI Alibaba **graph-core** 引擎按拓扑跑图，节点间经「状态黑板」传递数据。

```
拖拽编排（前端 Vue Flow 画布）
   ↓ 保存 graphData(JSON) → ai_workflow 表
POST /{id}/execute {input}
   ↓ graph-core 按边跑图
节点逐个执行：读 inputKey ← 黑板 → 处理 → 写 outputKey → 黑板
   ↓ SSE 实时推 node_start/node_complete/node_error
END 节点产出 → workflow_complete（对话回复 / 最终 state）
```

---

## 2. 架构与部署

| 项 | 值 |
|---|---|
| 服务名 | `aicoder-workflow` |
| 端口 | `8084` |
| 注册 | Nacos |
| Gateway 路由 | `/api/workflow/**` → `lb://aicoder-workflow` |
| 执行引擎 | Spring AI Alibaba **graph-core**（`OverAllState` 黑板 + 节点 `apply(state)`） |
| 数据库 | MySQL `test_ai.ai_workflow` / `ai_workflow_execution` / `ai_workflow_node_execution` / `ai_workflow_template` |
| 模型 | 复用 `DynamicModelRegistry`（DeepSeek / Ollama，配置同 chat） |
| 向量库 | 复用 `DynamicVectorStoreConfig`（Milvus / Chroma 等，供 RAG/NL2SQL 节点） |
| 鉴权 | Gateway 注入 `X-User-Id`（多端点 `defaultValue = "1"`） |

**依赖中间件**：MySQL、Redis、Nacos、（RAG/NL2SQL 节点需要）Milvus/Chroma 向量库、（LLM 节点需要）DeepSeek/Ollama。

---

## 3. 核心概念

| 概念 | 说明 |
|---|---|
| **工作流（Workflow）** | 一个 DAG 图，含 name/description/replyRequirements/graphData/status。状态 `DRAFT`/`PUBLISHED` |
| **节点（Node）** | 图中的一个处理单元，有 `type` + `config`。9 种类型（见 §4） |
| **边（Edge）** | 节点间的连线（source → target），决定执行顺序；条件节点按分支选边 |
| **状态黑板（OverAllState）** | 节点间共享的 key-value 存储。每个节点 `读 inputKey`、`写 outputKey`，以此串联数据流 |
| **inputKey/outputKey** | 节点的入/出字段名。如 LLM 节点 `inputKey=userInput, outputKey=llmResult`，下游节点用 `llmResult` 作输入 |
| **执行（Execution）** | 一次 `POST /{id}/execute`，生成一条 `WorkflowExecution`（状态 PENDING→RUNNING→COMPLETED/FAILED），每个节点一条 `WorkflowNodeExecution` |

**状态黑板是理解工作流的关键**：节点不直接互调，而是都读写同一块黑板。`START` 节点把请求 `input` 写入黑板（如 `userInput`），后续节点按 `inputKey` 取、按 `outputKey` 存，`END` 节点取最终值产出回复。

---

## 4. 节点类型（9 种）

`WorkflowNodeFactory` 按节点 `type`（小写字符串）构建。`START`/`END` 是图的起止终止节点。

| 节点 | type | 作用 | 关键 config |
|---|---|---|---|
| **开始** | `start` | 图入口，把请求 `input` 写入黑板 | （无） |
| **结束** | `end` | 图出口，产出最终回复 | 取黑板的某 key 作回复 |
| **LLM** | `llm` | 调大模型生成文本 | `promptTemplate`（支持 `{{input}}` 占位）、`inputKey`、`outputKey` |
| **RAG** | `rag` | 检索知识库，返回相关文档片段 | `knowledgeBaseId`、`inputKey`(query)、`outputKey` |
| **NL2SQL** | `nl2sql` | 自然语言→SQL→执行→返回结果 | `knowledgeBaseId`、`model`、`databaseName`、`inputKey`、`outputKey` |
| **条件** | `condition` | 按黑板值分支（多出口） | `inputKey`、`conditions`（分支条件列表） |
| **工具** | `tool` | 发起 HTTP 请求 | `url`、`method`、`headers`、`bodyTemplate`、`inputKey`、`outputKey` |
| **循环** | `loop` | 重复执行子流程直到退出条件 | `maxIterations`、`exitCondition`、`inputKey`、`outputKey` |
| **子工作流** | `subworkflow` | 调用另一个已发布工作流 | `workflowId`、`inputKey`、`outputKey` |

> 节点的 `config` 在前端 ConfigPanel 表单填写；保存进 `graphData` 的 `nodes[].data.config`。

**典型组合**：
- **LLM 直答**：START → LLM → END。
- **RAG 增强**：START → RAG(检索) → LLM(带检索结果生成) → END。
- **带分支**：START → LLM → CONDITION(判断意图) → [分支A: RAG→LLM] / [分支B: NL2SQL] → END。
- **调外部 API**：START → LLM(生成参数) → TOOL(调 API) → LLM(总结) → END。

---

## 5. REST API 参考

路径前缀 `/api/workflow`（Gateway :8080；`X-User-Id` 头由网关注入）。

### 5.1 工作流 CRUD

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/create` | 新建工作流（`WorkflowCreateRequest`：name/description/type/replyRequirements/graphData） |
| PUT | `/{id}` | 更新（含 graphData 改图） |
| DELETE | `/{id}` | 删除 |
| GET | `/list` | 当前用户的工作流列表 |
| GET | `/{id}` | 详情（含 graphData） |

### 5.2 执行

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/{id}/execute` | **执行工作流，SSE 流式返回**。请求体 `WorkflowExecutionRequest {input: Map}` |
| GET | `/executions/{executionId}` | 单次执行详情（状态/输入/输出） |
| GET | `/executions/{executionId}/nodes` | 该次执行的**逐节点执行记录**（每节点的输入/输出/状态/耗时） |
| GET | `/{id}/executions` | 某工作流的全部历史执行 |

### 5.3 模板

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/templates`（模板控制器根） | 模板列表（预置可复用工作流） |
| POST | `/templates` | 保存当前工作流为模板 |
| POST | `/templates/{id}/clone` | **从模板克隆出一个新工作流**（一键起步） |
| DELETE | `/templates/{id}` | 删模板 |

### 5.4 统计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/stats` | 当前用户的工作流统计（数量/执行次数等） |

```bash
# 列工作流
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/workflow/list
# 执行（SSE 流）
curl -N -X POST http://localhost:8080/api/workflow/3/execute \
  -H "Content-Type: application/json" -d '{"input":{"userInput":"你好"}}'
```

---

## 6. graphData 结构（Vue Flow JSON）

`graphData` 是前端 Vue Flow 画布的序列化结果，存 `ai_workflow.graph_data`（JSON 文本）：

```json
{
  "nodes": [
    { "id": "start_1", "type": "start", "position": {"x":250,"y":0}, "data": {"label":"开始","config":{}} },
    { "id": "llm_1", "type": "llm", "position": {"x":250,"y":200},
      "data": {"label":"生成回复","config":{
        "promptTemplate":"你是助手，回答：{{input}}",
        "inputKey":"userInput","outputKey":"reply"
      }}},
    { "id": "end_1", "type": "end", "position": {"x":250,"y":400}, "data": {"label":"结束","config":{}} }
  ],
  "edges": [
    { "source": "start_1", "target": "llm_1" },
    { "source": "llm_1",  "target": "end_1" }
  ]
}
```

- `nodes[].type` 决定节点类型（见 §4）；`nodes[].data.config` 是该节点的配置。
- `edges[].source/target` 决定执行顺序；条件节点的多出口由 config 的 `conditions` + 对应边表达。
- 执行时 `WorkflowNodeFactory` 遍历节点，按 `type` 构建 graph-core 节点实例。

---

## 7. 执行流程与 SSE 事件

`POST /{id}/execute` 返回 `text/event-stream`，事件类型：

| 事件 | 含义 |
|---|---|
| `node_start` | 某节点开始执行（含 nodeId/label） |
| `node_complete` | 某节点完成（含输出/耗时） |
| `node_error` | 某节点出错（含错误信息） |
| `workflow_complete` | 整个工作流完成（含最终回复 / 最终 state） |
| `workflow_error` | 工作流整体失败 |

执行过程：
1. 建一条 `WorkflowExecution`（status=RUNNING）。
2. graph-core 按 `START` 起步，把请求 `input` 写入 `OverAllState` 黑板。
3. 沿边拓扑执行：每节点读 `inputKey`←黑板→`apply()`→写 `outputKey`→黑板，同时落 `WorkflowNodeExecution` 记录 + 推 `node_start/complete`。
4. `END` 节点取黑板最终值，推 `workflow_complete`（回复或 state）。
5. `WorkflowExecution` 置 COMPLETED（或 FAILED）。

前端 `ExecutionPanel` 实时消费这些 SSE 事件，展示节点执行进度与输出。

---

## 8. 前端可视化编辑器

路由 `/workflow`（列表）、`/workflow/:id`（编辑器）。

| 组件 | 作用 |
|---|---|
| **WorkflowListView** | 工作流列表 + 「新建」（新工作流自带 START→END 骨架）+ 统计 |
| **WorkflowEditorView** | 编辑器主壳（顶部保存/发布/运行，左侧节点面板，中间画布，右侧配置，底部执行） |
| **NodePanel** | 节点面板：拖拽 LLM/RAG/NL2SQL/CONDITION/TOOL/LOOP/SUBWORKFLOW 到画布 |
| **FlowCanvas** | Vue Flow 画布：拖拽排布节点、连线（source→target）、缩放 |
| **ConfigPanel** | 选中节点后的配置表单（按节点 type 显示对应字段，如 LLM 的 promptTemplate/inputKey/outputKey） |
| **ExecutionPanel** | 点「运行」：填输入 → SSE 流式看每个节点的执行/输出/耗时 |
| **节点组件** | `StartNode`/`ToolNode`/`ConditionNode`/`LoopNode`…（画布上每种节点的视觉样式） |

**编辑流程**：拖节点 → 连线 → 选中节点填 config（注意 inputKey/outputKey 串联）→ 保存 → 「运行」填输入 → 看执行面板。

---

## 9. 配置与依赖

| 依赖 | 配置位置 | 说明 |
|---|---|---|
| LLM 模型 | `ai_model_config` 表（admin 管理页配置） | `DynamicModelRegistry` 按请求 modelCode 解析；LLM 节点用的模型在此注册 |
| 向量库 | `ai_vector_db_config` 表（admin 管理页） | RAG/NL2SQL 节点检索用；`DynamicVectorStoreConfig` 按 dbType 构建 |
| 知识库 | `ai_knowledge_base` 表（知识库管理页） | RAG/NL2SQL 节点的 `knowledgeBaseId` 指向这里 |
| DeepSeek key | `spring.ai.deepseek.api-key` | LLM 节点调 DeepSeek |

> **前置**：用 RAG/NL2SQL 节点前，须先在管理页配好向量库 + 建知识库 + 导入文档；LLM 节点须有可用模型。否则节点执行报错。

---

## 10. 运维与排障

| 现象 | 原因 / 处置 |
|---|---|
| LLM 节点报错/超时 | 模型未注册或 key 失效。查 `ai_model_config` + DeepSeek key；调大模型超时 |
| RAG/NL2SQL 节点无结果 | `knowledgeBaseId` 错或知识库无文档/向量库未配。去知识库管理页核对 |
| 执行 FAILED、看 `node_error` | 查该节点 `WorkflowNodeExecution` 的错误信息（`GET /executions/{eid}/nodes`） |
| 节点拿不到上游数据 | **inputKey/outputKey 没对上**。A 节点 outputKey 必须等于 B 节点 inputKey 才能传递——最常见坑 |
| 条件分支没走预期路径 | CONDITION 节点的 `conditions` 表达式 + 对应出边配置；核对条件取值 |
| 循环节点死循环 | `maxIterations` 兜底；检查 `exitCondition` 何时为真 |
| 工作流跑不出回复 | 缺 `END` 节点，或 END 取的 key 在黑板里不存在 |
| 子工作流调用失败 | `subworkflowId` 指向的工作流不存在/未保存；或入参 inputKey 不匹配 |

**关键日志关键字**：`node_start`/`node_complete`/`node_error`（节点级）、`workflow_complete`/`workflow_error`（整体）、`OverAllState`（黑板）。逐节点执行明细查 `GET /executions/{eid}/nodes`。

---

## 11. 数据模型

| 表 | 说明 |
|---|---|
| `ai_workflow` | 工作流定义：id/name/description/type/replyRequirements/**graphData(JSON)**/status(DRAFT/PUBLISHED)/userId |
| `ai_workflow_execution` | 一次执行：executionId/workflowId/status(PENDING/RUNNING/COMPLETED/FAILED/CANCELLED/SKIPPED)/input/output/耗时 |
| `ai_workflow_node_execution` | 逐节点执行：executionId/nodeId/nodeType/input/output/status/耗时/错误 |
| `ai_workflow_template` | 模板（可克隆的工作流样板） |

**状态枚举**：
- `WorkflowStatus`：`DRAFT`、`PUBLISHED`。
- `ExecutionStatus`：`PENDING`、`RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED`、`SKIPPED`。

---

## 附录：典型操作速查

```bash
# 列工作流
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/workflow/list

# 执行工作流 id=3（SSE 流，-N 不缓冲）
curl -N -X POST http://localhost:8080/api/workflow/3/execute \
  -H "Content-Type: application/json" \
  -d '{"input":{"userInput":"用一句话介绍 Spring Boot"}}'

# 看某次执行的逐节点明细
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/workflow/executions/<eid>/nodes

# 从模板克隆
curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/workflow/templates/<tid>/clone
```

**前端路径**：`/workflow`（列表）→ 点工作流进 `/workflow/:id` 编辑器 → 拖节点连线 → 填 config → 保存 → 「运行」。
