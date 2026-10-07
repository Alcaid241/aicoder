# aicoder-workflow 设计文档

## 概述

在现有 aicoder AI 助手平台中新增 aicoder-workflow 微服务，实现 AI Agent 可视化编排工作流系统。用户通过拖拽画布编排 DAG 工作流，组合 LLM 对话、RAG 检索等 AI 节点，一键执行并实时查看结果。

## 技术选型

| 层次 | 技术 | 说明 |
|------|------|------|
| 工作流引擎 | spring-ai-alibaba-graph | DAG 编排，State/Node/Edge 抽象，条件分支，流式执行 |
| 前端画布 | Vue Flow | Vue 3 原生拖拽流程图库 |
| 数据存储 | MySQL | 工作流定义 + 执行记录，复用现有 test_ai 库 |
| 实时通信 | SSE | 工作流执行过程流式推送节点状态 |
| 服务注册 | Nacos | 与现有微服务一致 |

## 整体架构

aicoder-workflow 是第 5 个微服务（端口 8084），通过 Gateway 统一对外：

```
前端 (aicoder-web)
  → API Gateway (:8080)
    → /api/admin/**    → aicoder-admin:8081
    → /api/chat/**     → aicoder-chat:8082
    → /api/rag/**      → aicoder-rag:8083
    → /api/workflow/** → aicoder-workflow:8084  ← 新增
```

与现有模块的关系：
- 独立部署，不跨服务 HTTP 调用其他模块
- 独立配置 ChatModel（与 aicoder-chat 相同的 Ollama/DeepSeek 模型）
- 独立配置 VectorStore（与 aicoder-rag 相同的 Redis/Chroma/Milvus 动态切换）

## 数据库设计

新增 3 张表，位于 test_ai 数据库：

### ai_workflow — 工作流定义

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| name | VARCHAR(100) NOT NULL | 工作流名称 |
| description | VARCHAR(500) | 描述 |
| graph_data | JSON NOT NULL | 画布完整数据（节点、边、位置） |
| status | VARCHAR(20) NOT NULL DEFAULT 'DRAFT' | DRAFT / PUBLISHED |
| user_id | BIGINT NOT NULL | 创建人 |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

### ai_workflow_execution — 执行记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| workflow_id | BIGINT NOT NULL | 关联工作流 |
| status | VARCHAR(20) NOT NULL DEFAULT 'RUNNING' | RUNNING / COMPLETED / FAILED / CANCELLED |
| input | JSON | 执行输入参数 |
| output | JSON | 最终输出结果 |
| error_message | TEXT | 错误信息 |
| user_id | BIGINT NOT NULL | 执行人 |
| started_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 开始时间 |
| finished_at | DATETIME | 结束时间 |

### ai_workflow_node_execution — 节点执行记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| execution_id | BIGINT NOT NULL | 关联执行记录 |
| node_id | VARCHAR(50) NOT NULL | 画布中的节点 ID |
| node_name | VARCHAR(100) | 节点名称 |
| node_type | VARCHAR(30) NOT NULL | LLM / RAG / NL2SQL / CONDITION / TOOL |
| status | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | PENDING / RUNNING / COMPLETED / FAILED / SKIPPED |
| input_data | JSON | 节点输入 |
| output_data | JSON | 节点输出 |
| error_message | TEXT | 错误信息 |
| started_at | DATETIME | 开始时间 |
| finished_at | DATETIME | 结束时间 |

设计说明：
- 节点和边的定义嵌入 graph_data JSON，不单独建表，因为它们与画布强绑定
- graph_data 由前端 Vue Flow 直接序列化/反序列化

## 后端设计

### 模块结构

```
aicoder-workflow/
  src/main/java/com/ai/coder/workflow/
    WorkflowApplication.java
    config/
      WorkflowModelConfig.java            -- 多模型注册
    controller/
      WorkflowController.java             -- 工作流 CRUD
      WorkflowExecutionController.java    -- 执行 + SSE
    service/
      WorkflowService.java                -- 增删改查
      WorkflowExecutionService.java       -- 解析 graph_data，构建 StateGraph 并执行
      WorkflowNodeFactory.java            -- 根据节点类型创建 NodeAction
    node/
      LlmNode.java                        -- LLM 对话节点
      RagNode.java                        -- RAG 检索节点
      Nl2SqlNode.java                     -- NL2SQL 节点 (第二期)
      ConditionNode.java                  -- 条件分支节点 (第二期)
      ToolNode.java                       -- 工具节点 (第二期)
    model/
      entity/
        Workflow.java
        WorkflowExecution.java
        WorkflowNodeExecution.java
      dto/
        WorkflowCreateRequest.java
        WorkflowExecutionRequest.java
      enums/
        NodeType.java
    repository/
      WorkflowRepository.java
      WorkflowExecutionRepository.java
      WorkflowNodeExecutionRepository.java
```

### 核心执行流程

```
1. 前端提交 graph_data (JSON) + 输入参数
2. WorkflowExecutionService 解析 graph_data 中的 nodes 和 edges
3. WorkflowNodeFactory 为每个节点创建对应的 NodeAction
4. 构建 StateGraph：
   - addNode() 添加所有节点
   - addEdge() 添加固定边
   - addConditionalEdges() 添加条件边 (第二期)
5. 编译并流式执行 compiledGraph.stream()
6. 通过 SSE 推送事件：
   - node_start:   { nodeId, nodeName }
   - node_complete: { nodeId, output }
   - node_error:    { nodeId, error }
   - workflow_complete: { output }
```

### 节点实现

**LlmNode**：从 State 取 inputKey 的值，填充 Prompt 模板，调用 ChatModel，结果写入 outputKey。

```java
public class LlmNode implements NodeAction {
    private final ChatModel chatModel;
    private final String promptTemplate;
    private final String inputKey;
    private final String outputKey;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String input = state.value(inputKey, "").toString();
        String prompt = promptTemplate.replace("{{input}}", input);
        String result = chatModel.call(prompt);
        return Map.of(outputKey, result);
    }
}
```

**RagNode**：从 State 取 inputKey 的值作为查询，调用 VectorStore.similaritySearch，检索结果拼接为上下文写入 outputKey。

```java
public class RagNode implements NodeAction {
    private final VectorStore vectorStore;
    private final String knowledgeBaseId;
    private final int topK;
    private final String inputKey;
    private final String outputKey;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = state.value(inputKey, "").toString();
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query).topK(topK)
                .filterExpression("knowledgeBaseId == " + knowledgeBaseId)
                .build()
        );
        String context = docs.stream().map(Document::getText).collect(joining("\n"));
        return Map.of(outputKey, context);
    }
}
```

### API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/workflow/create | 创建工作流 |
| PUT | /api/workflow/{id} | 更新工作流（保存画布） |
| DELETE | /api/workflow/{id} | 删除工作流 |
| GET | /api/workflow/list | 我的工作流列表 |
| GET | /api/workflow/{id} | 获取工作流详情 |
| POST | /api/workflow/{id}/execute | 执行工作流（SSE） |
| GET | /api/workflow/executions/{id} | 查看执行详情 |
| GET | /api/workflow/{id}/executions | 执行历史列表 |

## 前端设计

### 新增路由与页面

| 路径 | 页面 | 说明 |
|------|------|------|
| /workflow | WorkflowListView | 工作流列表页 |
| /workflow/:id | WorkflowEditorView | 编辑器页 |

侧边菜单新增"工作流"入口。

### 编辑器页面布局（三栏 + 底部面板）

```
+--------+---------------------------+----------+
| 节点   |                           | 属性     |
| 面板   |       Vue Flow 画布        | 配置     |
|        |                           | 面板     |
| [LLM]  |   [开始] --> [LLM] -->    |          |
| [RAG]  |            [RAG] -->      | 节点名称 |
| [SQL]* |            [结束]         | 模型选择 |
| [条件]* |                          | Prompt   |
| [工具]* |                          | 输入键   |
|        |                           | 输出键   |
+--------+---------------------------+----------+
|            底部：执行面板                     |
| [运行] 输入: [________]                      |
| 节点1 ✓ 3.2s → 节点2 ⏳ → 节点3 ○           |
| 输出: ...                                    |
+---------------------------------------------+
  (* 第二期节点)
```

### 前端文件结构

```
src/
  api/
    workflow.ts
  views/
    workflow/
      WorkflowListView.vue
      WorkflowEditorView.vue
      components/
        FlowCanvas.vue
        NodePanel.vue
        ConfigPanel.vue
        ExecutionPanel.vue
        nodes/
          LlmNode.vue
          RagNode.vue
          StartNode.vue
          EndNode.vue
  stores/
    workflowStore.ts
  types/
    workflow.ts
```

### graph_data JSON 结构

```json
{
  "nodes": [
    {
      "id": "node_1",
      "type": "llm",
      "position": { "x": 200, "y": 100 },
      "data": {
        "label": "总结分析",
        "config": {
          "model": "deepseek-v4-flash",
          "promptTemplate": "请根据以下内容进行总结：{{input}}",
          "inputKey": "query",
          "outputKey": "summary"
        }
      }
    }
  ],
  "edges": [
    { "id": "edge_1", "source": "node_1", "target": "node_2" }
  ]
}
```

### 交互流程

1. 从左侧面板拖拽节点到画布
2. 点击节点，右侧显示该类型的配置表单
3. 从节点输出端口拖线到另一节点输入端口，创建连线
4. 点击"保存"序列化为 graph_data
5. 底部面板输入参数，点击"运行"
6. SSE 实时更新节点状态（颜色变化：灰→蓝→绿/红）
7. 完成后底部显示最终输出

## 分期交付计划

### 第一期（MVP）

- aicoder-workflow 微服务骨架 + Nacos 注册
- spring-ai-alibaba-graph 集成
- 工作流 CRUD API
- 工作流执行引擎（解析 graph_data → StateGraph → 流式执行）
- LlmNode（模型选择、Prompt 模板、输入输出键）
- RagNode（知识库选择、topK、输入输出键）
- SSE 流式推送节点执行状态
- 执行历史记录
- Vue Flow 画布编辑器（拖拽节点、连线）
- 左侧节点面板（LLM / RAG / 开始 / 结束）
- 右侧属性配置面板
- 底部执行面板
- Gateway 路由配置
- 数据库建表 SQL
- 侧边菜单更新

### 第二期

- NL2SQL 节点
- 条件分支节点
- 工具节点（HTTP 调用 / 脚本执行）

### 第三期

- 子工作流
- 循环节点
- 执行历史回放（可视化重放节点状态）
- 工作流导入导出
