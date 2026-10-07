# 工作流第二期 — NL2SQL / 条件分支 / 工具节点 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 aicoder-workflow 添加三种新节点类型：NL2SQL（自然语言转 SQL 并执行）、条件分支（根据输出动态路由）、工具节点（HTTP 请求调用）。

**Architecture:** 后端新增 Nl2SqlNode、ConditionNode、ToolNode 三个 NodeAction 实现，WorkflowNodeFactory 扩展 switch 分支，WorkflowExecutionService 支持条件边（`addConditionalEdges`）。前端新增对应节点组件、配置表单、拖拽项。条件分支的边数据结构增加 `sourceHandle` 字段标识分支路径。

**Tech Stack:** Java 17, Spring Boot 3.5, spring-ai-alibaba-graph 1.1.2.2, Vue 3.5, Vue Flow, TypeScript

---

## File Map

### Backend (新建文件)

```
aicoder-workflow/src/main/java/com/ai/coder/workflow/
  node/
    Nl2SqlNode.java                          -- NL2SQL 节点
    ConditionNode.java                       -- 条件分支节点（透传 state，不做计算）
    ToolNode.java                            -- HTTP 工具节点
  service/
    WorkflowSqlService.java                  -- SQL 生成与执行（从 rag 模块移植核心逻辑）
```

### Backend (修改文件)

```
aicoder-workflow/src/main/java/com/ai/coder/workflow/
  service/WorkflowNodeFactory.java           -- 添加 nl2sql / condition / tool 分支
  service/WorkflowExecutionService.java      -- 支持 condition 类型的条件边
```

### Frontend (新建文件)

```
aicoder-web/src/views/workflow/components/nodes/
  Nl2SqlNode.vue                             -- NL2SQL 画布节点组件
  ConditionNode.vue                          -- 条件分支画布节点组件
  ToolNode.vue                               -- 工具节点画布组件
```

### Frontend (修改文件)

```
aicoder-web/src/types/workflow.ts            -- 扩展 WorkflowNodeConfig
aicoder-web/src/views/workflow/components/FlowCanvas.vue    -- 注册新节点类型
aicoder-web/src/views/workflow/components/NodePanel.vue     -- 添加新节点拖拽项
aicoder-web/src/views/workflow/components/ConfigPanel.vue   -- 添加新节点配置表单
```

---

## Task 1: 后端 Nl2SqlNode

**Files:**
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowSqlService.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/node/Nl2SqlNode.java`

Nl2SqlNode 将复用 rag 模块的 NL2SQL 核心逻辑（DDL 向量检索 + LLM 生成 SQL + 执行），但作为独立的 NodeAction 实现嵌入工作流执行。

- [ ] **Step 1: 创建 WorkflowSqlService**

从 rag 模块移植 SQL 生成与执行的核心逻辑，适配 workflow 模块的依赖注入。

```java
package com.ai.coder.workflow.service;

import com.ai.coder.workflow.config.DynamicVectorStoreConfig;
import com.ai.coder.workflow.model.entity.KnowledgeBase;
import com.ai.coder.workflow.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSqlService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DynamicVectorStoreConfig dynamicVectorStoreConfig;
    private final Map<String, ChatModel> modelMap;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private static final String SQL_SYSTEM_PROMPT = """
            你是一个专业的 SQL 生成助手。根据提供的数据库 DDL 信息，将用户的自然语言问题转换为 SQL 查询语句。

            规则：
            1. 只生成 SELECT 查询语句，严禁生成 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE 等修改数据的语句。
            2. 如果用户的问题存在歧义，请设置 "clarification" 字段说明歧义内容，并在 "options" 字段中提供可能的选项。
            3. 如果用户的问题与数据库查询无关、涉及敏感操作、或无法根据 DDL 生成合理的 SQL，请设置 "isRejected" 为 true。
            4. 生成的 SQL 必须符合 MySQL 语法规范。
            5. 请严格按以下 JSON 格式返回，不要包含任何其他内容：
            {
              "sql": "生成的SQL语句，如果没有则留空",
              "clarification": "歧义说明或拒绝原因，如果没有则留空",
              "isRejected": false,
              "canExecute": true
            }

            参考的数据库 DDL 结构：
            %s
            """;

    public String generateAndExecute(String question, String knowledgeBaseId, String modelName,
                                      String databaseName) throws Exception {
        KnowledgeBase kb = knowledgeBaseRepository.findById(Long.parseLong(knowledgeBaseId))
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + knowledgeBaseId));

        if (!"DDL".equals(kb.getType())) {
            throw new IllegalArgumentException("只有 DDL 类型的知识库支持 SQL 生成");
        }

        VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore();
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question).topK(10)
                        .filterExpression("knowledgeBaseId == '" + knowledgeBaseId + "'")
                        .build()
        );

        String ddlContext = docs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        String systemPrompt = String.format(SQL_SYSTEM_PROMPT, ddlContext);

        ChatModel chatModel = modelMap.get(modelName);
        if (chatModel == null) chatModel = modelMap.values().iterator().next();

        String responseText = chatModel.call(
                new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(question)))
        ).getResult().getOutput().getText();

        String sql = parseSql(responseText);
        if (sql == null || sql.isBlank()) {
            throw new RuntimeException("无法生成有效的 SQL 语句");
        }

        log.info("NL2SQL 生成 SQL: {}", sql);
        return executeSql(sql, databaseName);
    }

    private String parseSql(String responseText) {
        try {
            String json = responseText.trim();
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                json = json.substring(0, json.indexOf("```"));
            }
            json = json.trim();
            var node = objectMapper.readTree(json);
            if (node.has("isRejected") && node.get("isRejected").asBoolean()) {
                throw new RuntimeException("SQL 生成被拒绝: " + node.path("clarification").asText());
            }
            return node.path("sql").asText("");
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            log.warn("解析 SQL 响应失败: {}", e.getMessage());
            return responseText.trim();
        }
    }

    private String executeSql(String sql, String databaseName) throws Exception {
        String normalized = sql.trim().toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("SHOW")
                && !normalized.startsWith("DESCRIBE") && !normalized.startsWith("EXPLAIN")) {
            throw new IllegalArgumentException("只允许执行 SELECT 查询语句");
        }

        try (Connection conn = dataSource.getConnection()) {
            if (databaseName != null && !databaseName.isBlank()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("USE " + databaseName);
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                List<Map<String, String>> rows = new ArrayList<>();
                int count = 0;
                while (rs.next() && count < 200) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        row.put(columns.get(i - 1), val != null ? val.toString() : null);
                    }
                    rows.add(row);
                    count++;
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("sql", sql);
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("totalRows", count);
                return objectMapper.writeValueAsString(result);
            }
        }
    }
}
```

- [ ] **Step 2: 创建 Nl2SqlNode**

```java
package com.ai.coder.workflow.node;

import com.ai.coder.workflow.service.WorkflowSqlService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Nl2SqlNode implements NodeAction {

    private final WorkflowSqlService sqlService;
    private final String knowledgeBaseId;
    private final String model;
    private final String databaseName;
    private final String inputKey;
    private final String outputKey;

    public Nl2SqlNode(WorkflowSqlService sqlService, String knowledgeBaseId, String model,
                      String databaseName, String inputKey, String outputKey) {
        this.sqlService = sqlService;
        this.knowledgeBaseId = knowledgeBaseId;
        this.model = model;
        this.databaseName = databaseName;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(inputKey, "").toString();
        log.info("Nl2SqlNode 执行: question={}, kbId={}, model={}", question, knowledgeBaseId, model);

        String result = sqlService.generateAndExecute(question, knowledgeBaseId, model, databaseName);

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, result);
        return output;
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/node/Nl2SqlNode.java aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowSqlService.java
git commit -m "feat(workflow): 添加 NL2SQL 节点和 SQL 服务"
```

---

## Task 2: 后端 ConditionNode

**Files:**
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/node/ConditionNode.java`

条件分支节点不做实际计算，它的作用是：接收上游输出，根据配置的 conditionField 从 state 中取值，将结果写入一个约定的路由键（`__route__`），供 `addConditionalEdges` 的路由函数读取。

config 结构：
```json
{
  "conditionField": "result",
  "conditions": [
    { "label": "分支A", "match": "positive" },
    { "label": "分支B", "match": "negative" },
    { "label": "默认", "match": "__default__" }
  ],
  "inputKey": "result"
}
```

- [ ] **Step 1: 创建 ConditionNode**

```java
package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ConditionNode implements NodeAction {

    private final String inputKey;
    private final List<Map<String, String>> conditions;

    public ConditionNode(String inputKey, List<Map<String, String>> conditions) {
        this.inputKey = inputKey;
        this.conditions = conditions;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object inputValue = state.value(inputKey, "");
        String value = inputValue != null ? inputValue.toString() : "";

        String matchedRoute = "__default__";
        for (Map<String, String> cond : conditions) {
            String match = cond.get("match");
            if (!"__default__".equals(match) && value.contains(match)) {
                matchedRoute = cond.get("label");
                break;
            }
        }

        // 如果没有显式匹配，检查是否有 __default__ 分支
        if ("__default__".equals(matchedRoute)) {
            for (Map<String, String> cond : conditions) {
                if ("__default__".equals(cond.get("match"))) {
                    matchedRoute = cond.get("label");
                    break;
                }
            }
        }

        log.info("ConditionNode 执行: inputKey={}, value='{}', route={}", inputKey, value, matchedRoute);

        Map<String, Object> output = new HashMap<>();
        output.put("__route__", matchedRoute);
        return output;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/node/ConditionNode.java
git commit -m "feat(workflow): 添加条件分支节点"
```

---

## Task 3: 后端 ToolNode

**Files:**
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/node/ToolNode.java`

工具节点执行 HTTP 请求。config 包含 url、method、headers、bodyTemplate、inputKey、outputKey。bodyTemplate 支持 `{{input}}` 占位符替换。

- [ ] **Step 1: 创建 ToolNode**

```java
package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ToolNode implements NodeAction {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final String bodyTemplate;
    private final String inputKey;
    private final String outputKey;

    public ToolNode(String url, String method, Map<String, String> headers,
                    String bodyTemplate, String inputKey, String outputKey) {
        this.url = url;
        this.method = method != null ? method.toUpperCase() : "GET";
        this.headers = headers != null ? headers : Map.of();
        this.bodyTemplate = bodyTemplate;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(inputKey, "").toString();
        String body = bodyTemplate != null ? bodyTemplate.replace("{{input}}", input) : input;

        log.info("ToolNode 执行: method={}, url={}, inputLength={}", method, url, input.length());

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        for (Map.Entry<String, String> h : headers.entrySet()) {
            requestBuilder.header(h.getKey(), h.getValue());
        }

        if ("GET".equals(method)) {
            requestBuilder.GET();
        } else if ("POST".equals(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
            if (!headers.containsKey("Content-Type")) {
                requestBuilder.header("Content-Type", "application/json");
            }
        } else if ("PUT".equals(method)) {
            requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
        } else if ("DELETE".equals(method)) {
            requestBuilder.DELETE();
        }

        HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        log.info("ToolNode 响应: status={}", response.statusCode());

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, response.body());
        return output;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/node/ToolNode.java
git commit -m "feat(workflow): 添加 HTTP 工具节点"
```

---

## Task 4: 后端 WorkflowNodeFactory 扩展

**Files:**
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java`

在现有 switch 中添加 `nl2sql`、`condition`、`tool` 三个分支。

- [ ] **Step 1: 修改 WorkflowNodeFactory**

添加 import 语句和三个新的工厂方法：

在文件顶部的 import 区域添加：

```java
import com.ai.coder.workflow.node.Nl2SqlNode;
import com.ai.coder.workflow.node.ConditionNode;
import com.ai.coder.workflow.node.ToolNode;
import com.ai.coder.workflow.service.WorkflowSqlService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
```

给类添加新的依赖注入字段（在 `private final ObjectMapper objectMapper;` 之后）：

```java
private final WorkflowSqlService sqlService;
```

修改 `createNode` 方法的 switch 块：

```java
return switch (type) {
    case "llm" -> createLlmNode(config);
    case "rag" -> createRagNode(config);
    case "nl2sql" -> createNl2SqlNode(config);
    case "condition" -> createConditionNode(config);
    case "tool" -> createToolNode(config);
    default -> throw new IllegalArgumentException("不支持的节点类型: " + type);
};
```

在 `createRagNode` 方法后添加三个新方法：

```java
private Nl2SqlNode createNl2SqlNode(JsonNode config) {
    String knowledgeBaseId = config.path("knowledgeBaseId").asText();
    String model = config.path("model").asText("deepseek-v4-flash");
    String databaseName = config.path("databaseName").asText("");
    String inputKey = config.path("inputKey").asText("query");
    String outputKey = config.path("outputKey").asText("sqlResult");

    return new Nl2SqlNode(sqlService, knowledgeBaseId, model, databaseName, inputKey, outputKey);
}

private ConditionNode createConditionNode(JsonNode config) {
    String inputKey = config.path("inputKey").asText("result");
    List<Map<String, String>> conditions = new ArrayList<>();
    JsonNode conditionsNode = config.path("conditions");
    if (conditionsNode.isArray()) {
        for (JsonNode cond : conditionsNode) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("label", cond.path("label").asText());
            entry.put("match", cond.path("match").asText());
            conditions.add(entry);
        }
    }
    return new ConditionNode(inputKey, conditions);
}

private ToolNode createToolNode(JsonNode config) {
    String url = config.path("url").asText();
    String method = config.path("method").asText("GET");
    String bodyTemplate = config.path("bodyTemplate").asText("");

    Map<String, String> headers = new LinkedHashMap<>();
    JsonNode headersNode = config.path("headers");
    if (headersNode.isObject()) {
        headersNode.fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
    }

    String inputKey = config.path("inputKey").asText("query");
    String outputKey = config.path("outputKey").asText("httpResult");

    return new ToolNode(url, method, headers, bodyTemplate, inputKey, outputKey);
}
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java
git commit -m "feat(workflow): NodeFactory 支持 NL2SQL/条件分支/工具节点"
```

---

## Task 5: 后端 WorkflowExecutionService 支持条件边

**Files:**
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowExecutionService.java`

条件分支节点的边需要用 `addConditionalEdges` 而不是 `addEdge`。需要修改 `executeGraph` 方法中的边构建逻辑。

前端 graph_data 的边（edge）结构增加可选字段 `sourceHandle`，当 source 是 condition 节点时，`sourceHandle` 的值就是分支 label。例如：

```json
{
  "id": "e1",
  "source": "condition_1",
  "sourceHandle": "分支A",
  "target": "llm_1"
}
```

后端解析逻辑：找出所有从 condition 节点出发的边，按 sourceHandle 分组，构建 `addConditionalEdges` 调用。

- [ ] **Step 1: 修改 executeGraph 方法**

在 `executeGraph` 方法中，找到当前构建边的部分（即 `// 构建边映射` 注释之后到 `CompiledGraph compiledGraph` 之前的代码），替换为以下逻辑：

```java
        // 收集从 start 节点出发的第一条边
        String startNode = null;
        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();
            if ("start".equals(nodeTypeMap.get(source))) {
                startNode = target;
            }
        }

        if (startNode != null) {
            stateGraph.addEdge(StateGraph.START, startNode);
        }

        // 收集从 condition 节点出发的条件边
        Map<String, Map<String, String>> conditionalEdgesMap = new LinkedHashMap<>();

        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();

            // 跳过 start 和 end 相关的边
            if ("start".equals(nodeTypeMap.get(source))) continue;
            if ("end".equals(nodeTypeMap.get(target))) {
                // 非 condition 节点连接到 end
                if (!"condition".equals(nodeTypeMap.get(source))) {
                    stateGraph.addEdge(source, StateGraph.END);
                }
                continue;
            }

            // condition 节点的边走条件分支
            if ("condition".equals(nodeTypeMap.get(source))) {
                String sourceHandle = edge.path("sourceHandle").asText("__default__");
                conditionalEdgesMap.computeIfAbsent(source, k -> new LinkedHashMap<>())
                        .put(sourceHandle, target);
            } else {
                // 普通边
                stateGraph.addEdge(source, target);
            }
        }

        // 添加条件边
        for (Map.Entry<String, Map<String, String>> entry : conditionalEdgesMap.entrySet()) {
            String conditionNodeId = entry.getKey();
            Map<String, String> mappings = entry.getValue();

            stateGraph.addConditionalEdges(conditionNodeId,
                    com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async(state -> {
                        return state.value("__route__", "__default__").toString();
                    }),
                    mappings
            );
        }
```

同时在 KeyStrategy 构建部分，确保 `__route__` 键被注册。在 `addIfPresent` 调用之后添加：

```java
allKeys.add("__route__");
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowExecutionService.java
git commit -m "feat(workflow): 执行引擎支持条件分支边"
```

---

## Task 6: 前端类型扩展

**Files:**
- Modify: `aicoder-web/src/types/workflow.ts`

扩展 `WorkflowNodeConfig` 接口，添加新节点类型的配置字段。

- [ ] **Step 1: 修改 WorkflowNodeConfig**

将现有的 `WorkflowNodeConfig` 接口替换为：

```typescript
export interface WorkflowNodeConfig {
  // LLM
  model?: string
  promptTemplate?: string
  inputKey?: string
  outputKey?: string
  // RAG
  knowledgeBaseId?: string
  topK?: number
  // NL2SQL
  databaseName?: string
  // Condition
  conditions?: { label: string; match: string }[]
  // Tool (HTTP)
  url?: string
  method?: string
  headers?: Record<string, string>
  bodyTemplate?: string
}
```

同时修改 `FlowEdge` 接口添加 `sourceHandle`：

```typescript
export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
}
```

- [ ] **Step 2: Commit**

```bash
git add aicoder-web/src/types/workflow.ts
git commit -m "feat(workflow): 扩展前端类型定义支持新节点"
```

---

## Task 7: 前端 NL2SQL / 条件分支 / 工具节点画布组件

**Files:**
- Create: `aicoder-web/src/views/workflow/components/nodes/Nl2SqlNode.vue`
- Create: `aicoder-web/src/views/workflow/components/nodes/ConditionNode.vue`
- Create: `aicoder-web/src/views/workflow/components/nodes/ToolNode.vue`

- [ ] **Step 1: 创建 Nl2SqlNode.vue**

```vue
<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
</script>

<template>
  <div class="workflow-node nl2sql-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge sql">SQL</span>
      <span class="node-title">{{ props.data?.label || 'NL2SQL' }}</span>
    </div>
    <div class="node-info">
      <span v-if="props.data?.config?.knowledgeBaseId" class="node-tag">KB #{{ props.data.config.knowledgeBaseId }}</span>
      <span v-if="props.data?.config?.model" class="node-tag">{{ props.data.config.model }}</span>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped lang="scss">
.nl2sql-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 160px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.sql { background: #e0e7ff; color: #4338ca; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
}
</style>
```

- [ ] **Step 2: 创建 ConditionNode.vue**

条件分支节点有多个输出端口（每个分支一个），使用 Vue Flow 的 `id` 属性在 Handle 上设置 sourceHandle 标识。

```vue
<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
const conditions = props.data?.config?.conditions || []
</script>

<template>
  <div class="workflow-node condition-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge cond">条件</span>
      <span class="node-title">{{ props.data?.label || '条件分支' }}</span>
    </div>
    <div v-if="conditions.length > 0" class="branch-list">
      <div v-for="cond in conditions" :key="cond.label" class="branch-item">
        <span class="branch-label">{{ cond.label }}</span>
        <span class="branch-match">{{ cond.match === '__default__' ? '其他' : cond.match }}</span>
        <Handle type="source" :position="Position.Right" :id="cond.label" class="branch-handle" />
      </div>
    </div>
    <div v-else class="node-info">
      <span class="node-tag">未配置分支</span>
      <Handle type="source" :position="Position.Bottom" id="__default__" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.condition-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 180px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.cond { background: #fef3c7; color: #92400e; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
}
.branch-list { display: flex; flex-direction: column; gap: 6px; }
.branch-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 4px 8px; background: #fefce8; border: 1px solid #fde68a; border-radius: 4px;
  position: relative;
  .branch-label { font-size: 12px; font-weight: 600; color: #92400e; }
  .branch-match { font-size: 11px; color: var(--text-muted); }
}
.branch-handle {
  position: absolute !important;
  right: -20px;
  top: 50%;
  transform: translateY(-50%);
  width: 10px;
  height: 10px;
}
</style>
```

- [ ] **Step 3: 创建 ToolNode.vue**

```vue
<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
</script>

<template>
  <div class="workflow-node tool-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge tool">HTTP</span>
      <span class="node-title">{{ props.data?.label || 'HTTP 请求' }}</span>
    </div>
    <div class="node-info">
      <span v-if="props.data?.config?.method" class="node-tag">{{ props.data.config.method }}</span>
      <span v-if="props.data?.config?.url" class="node-tag node-tag-url">{{ props.data.config.url }}</span>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped lang="scss">
.tool-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 160px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.tool { background: #fce7f3; color: #9d174d; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; flex-wrap: wrap; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
  .node-tag-url { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
</style>
```

- [ ] **Step 4: Commit**

```bash
git add aicoder-web/src/views/workflow/components/nodes/Nl2SqlNode.vue aicoder-web/src/views/workflow/components/nodes/ConditionNode.vue aicoder-web/src/views/workflow/components/nodes/ToolNode.vue
git commit -m "feat(workflow): 添加 NL2SQL/条件分支/工具节点画布组件"
```

---

## Task 8: 前端 FlowCanvas 注册新节点 + NodePanel 拖拽项

**Files:**
- Modify: `aicoder-web/src/views/workflow/components/FlowCanvas.vue`
- Modify: `aicoder-web/src/views/workflow/components/NodePanel.vue`

- [ ] **Step 1: 修改 FlowCanvas.vue**

添加 import 语句（在现有 import 后面添加）：

```typescript
import Nl2SqlNode from './nodes/Nl2SqlNode.vue'
import ConditionNode from './nodes/ConditionNode.vue'
import ToolNode from './nodes/ToolNode.vue'
```

在 `nodeTypes` 对象中添加三个新类型：

```typescript
const nodeTypes: Record<string, any> = {
  start: StartNode,
  end: EndNode,
  llm: LlmNode,
  rag: RagNode,
  nl2sql: Nl2SqlNode,
  condition: ConditionNode,
  tool: ToolNode,
}
```

修改 `onDrop` 中的 labels 映射：

```typescript
const labels: Record<string, string> = {
  llm: 'LLM 对话',
  rag: '知识检索',
  nl2sql: 'NL2SQL',
  condition: '条件分支',
  tool: 'HTTP 请求',
}
```

- [ ] **Step 2: 修改 NodePanel.vue**

在 `nodeItems` 数组中添加三个新项：

```typescript
const nodeItems = [
  {
    type: 'llm',
    label: 'LLM 对话',
    badge: 'LLM',
    badgeClass: 'llm',
    defaultConfig: { model: 'deepseek-v4-flash', promptTemplate: '请回答以下问题：{{input}}', inputKey: 'query', outputKey: 'result' }
  },
  {
    type: 'rag',
    label: '知识检索',
    badge: 'RAG',
    badgeClass: 'rag',
    defaultConfig: { knowledgeBaseId: '', topK: 5, inputKey: 'query', outputKey: 'context' }
  },
  {
    type: 'nl2sql',
    label: 'NL2SQL',
    badge: 'SQL',
    badgeClass: 'sql',
    defaultConfig: { knowledgeBaseId: '', model: 'deepseek-v4-flash', databaseName: '', inputKey: 'query', outputKey: 'sqlResult' }
  },
  {
    type: 'condition',
    label: '条件分支',
    badge: '条件',
    badgeClass: 'cond',
    defaultConfig: { inputKey: 'result', conditions: [{ label: '分支A', match: '' }, { label: '默认', match: '__default__' }] }
  },
  {
    type: 'tool',
    label: 'HTTP 请求',
    badge: 'HTTP',
    badgeClass: 'tool',
    defaultConfig: { url: '', method: 'GET', headers: {}, bodyTemplate: '', inputKey: 'query', outputKey: 'httpResult' }
  }
]
```

在 `<style>` 部分的 `.badge` 类中添加新颜色：

```scss
&.sql { background: #e0e7ff; color: #4338ca; }
&.cond { background: #fef3c7; color: #92400e; }
&.tool { background: #fce7f3; color: #9d174d; }
```

- [ ] **Step 3: 验证前端编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npm run build`
Expected: 编译成功，无错误

- [ ] **Step 4: Commit**

```bash
git add aicoder-web/src/views/workflow/components/FlowCanvas.vue aicoder-web/src/views/workflow/components/NodePanel.vue
git commit -m "feat(workflow): 画布和面板注册三种新节点"
```

---

## Task 9: 前端 ConfigPanel 新节点配置表单

**Files:**
- Modify: `aicoder-web/src/views/workflow/components/ConfigPanel.vue`

为三种新节点添加配置表单。

- [ ] **Step 1: 在 ConfigPanel.vue 的 template 中添加新节点表单**

在 `</template>` (rag 配置的 `</template>` 闭合标签) 之后、`<button class="btn-delete"` 之前，添加三段新的配置模板：

```html
      <template v-if="node.type === 'nl2sql'">
        <div class="form-group">
          <label>模型</label>
          <select :value="node.data.config.model" @change="updateConfig('model', ($event.target as HTMLSelectElement).value)">
            <option value="deepseek-v4-flash">DeepSeek V4 Flash</option>
            <option value="gemma3:4b">Gemma3 4B</option>
          </select>
        </div>
        <div class="form-group">
          <label>知识库 ID (DDL)</label>
          <input :value="node.data.config.knowledgeBaseId" @input="updateConfig('knowledgeBaseId', ($event.target as HTMLInputElement).value)" />
          <p class="hint">填入 DDL 类型的知识库 ID</p>
        </div>
        <div class="form-group">
          <label>数据库名</label>
          <input :value="node.data.config.databaseName" @input="updateConfig('databaseName', ($event.target as HTMLInputElement).value)" />
          <p class="hint">留空使用默认数据库</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'condition'">
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
          <p class="hint">读取上游节点的哪个输出值做判断</p>
        </div>
        <div class="form-group">
          <label>分支条件</label>
          <div class="condition-list">
            <div v-for="(cond, idx) in node.data.config.conditions" :key="idx" class="condition-row">
              <input class="cond-label" :value="cond.label" placeholder="分支名称"
                @input="updateCondition(idx, 'label', ($event.target as HTMLInputElement).value)" />
              <input class="cond-match" :value="cond.match === '__default__' ? '' : cond.match" placeholder="匹配关键词"
                @input="updateCondition(idx, 'match', ($event.target as HTMLInputElement).value)" />
              <button v-if="node.data.config.conditions.length > 1" class="cond-remove" @click="removeCondition(idx)">×</button>
            </div>
          </div>
          <button class="btn-add" @click="addCondition">+ 添加分支</button>
          <p class="hint">留空匹配 = 默认分支（其他都不匹配时走）</p>
        </div>
      </template>

      <template v-if="node.type === 'tool'">
        <div class="form-group">
          <label>请求方式</label>
          <select :value="node.data.config.method" @change="updateConfig('method', ($event.target as HTMLSelectElement).value)">
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
          </select>
        </div>
        <div class="form-group">
          <label>URL</label>
          <input :value="node.data.config.url" @input="updateConfig('url', ($event.target as HTMLInputElement).value)" placeholder="https://api.example.com/data" />
        </div>
        <div class="form-group">
          <label>请求头 (JSON)</label>
          <textarea rows="2" :value="headersJson" @input="updateHeaders(($event.target as HTMLTextAreaElement).value)" placeholder='{"Authorization": "Bearer xxx"}' />
        </div>
        <div class="form-group">
          <label>请求体模板</label>
          <textarea rows="3" :value="node.data.config.bodyTemplate"
            @input="updateConfig('bodyTemplate', ($event.target as HTMLTextAreaElement).value)" />
          <p class="hint">使用 &#123;&#123;input&#125;&#125; 作为输入变量占位符</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>
```

- [ ] **Step 2: 在 ConfigPanel.vue 的 script 中添加辅助方法**

在 `handleDeleteNode` 函数后添加：

```typescript
import { computed, ref } from 'vue'

// condition 节点的辅助方法
const updateCondition = (idx: number, field: string, value: string) => {
  if (!node.value) return
  const conditions = [...(node.value.data.config.conditions || [])]
  if (field === 'match' && !value) value = '__default__'
  conditions[idx] = { ...conditions[idx], [field]: value }
  updateConfig('conditions', conditions)
}

const addCondition = () => {
  if (!node.value) return
  const conditions = [...(node.value.data.config.conditions || [])]
  conditions.push({ label: `分支${conditions.length + 1}`, match: '' })
  updateConfig('conditions', conditions)
}

const removeCondition = (idx: number) => {
  if (!node.value) return
  const conditions = [...(node.value.data.config.conditions || [])]
  conditions.splice(idx, 1)
  updateConfig('conditions', conditions)
}

// tool 节点的 headers JSON 辅助
const headersJson = computed(() => {
  if (!node.value?.data?.config?.headers) return '{}'
  return JSON.stringify(node.value.data.config.headers, null, 2)
})

const updateHeaders = (value: string) => {
  try {
    const parsed = JSON.parse(value)
    updateConfig('headers', parsed)
  } catch { /* ignore invalid JSON */ }
}
```

注意：确保 `import { computed } from 'vue'` 中增加了 `ref`（如果原本只有 `computed`）。

- [ ] **Step 3: 在 ConfigPanel 的 style 中添加新样式**

在 `<style>` 末尾添加：

```scss
.condition-list { display: flex; flex-direction: column; gap: 6px; }
.condition-row {
  display: flex; gap: 4px; align-items: center;
  .cond-label { width: 90px; }
  .cond-match { flex: 1; }
  input { padding: 5px 8px; border: 1px solid var(--border-subtle); border-radius: 4px; font-size: 12px; background: var(--bg-primary); color: var(--text-primary); }
}
.cond-remove {
  width: 22px; height: 22px; border: none; border-radius: 4px;
  background: #fee2e2; color: #dc2626; cursor: pointer; font-size: 14px;
  display: flex; align-items: center; justify-content: center;
}
.btn-add {
  margin-top: 6px; width: 100%; padding: 6px; border: 1px dashed var(--border-subtle);
  border-radius: 6px; background: transparent; color: var(--text-secondary);
  font-size: 12px; cursor: pointer;
  &:hover { border-color: var(--text-secondary); }
}
```

- [ ] **Step 4: 验证前端编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npm run build`
Expected: 编译成功

- [ ] **Step 5: Commit**

```bash
git add aicoder-web/src/views/workflow/components/ConfigPanel.vue
git commit -m "feat(workflow): 配置面板支持 NL2SQL/条件分支/工具节点"
```

---

## Task 10: 端到端验证

- [ ] **Step 1: 启动所有服务并验证**

1. 启动基础中间件（MySQL、Redis、Nacos、Ollama）
2. 启动 aicoder-workflow (8084) 和 aicoder-gateway (8080)
3. 启动前端 `npm run dev`

- [ ] **Step 2: 验证新节点拖拽和配置**

1. 打开工作流编辑器
2. 从左侧面板拖拽 NL2SQL、条件分支、HTTP 请求节点到画布
3. 点击每个节点，在右侧配置面板填写配置
4. 保存工作流

- [ ] **Step 3: 验证条件分支连线**

1. 创建一个包含条件分支的工作流：开始 → LLM → 条件分支 → (分支A) → LLM → 结束 / (分支B) → RAG → 结束
2. 从条件分支节点的右侧端口拖线到下游节点
3. 确认边带有 `sourceHandle` 标识

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(workflow): 第二期 — NL2SQL/条件分支/工具节点完成"
```
