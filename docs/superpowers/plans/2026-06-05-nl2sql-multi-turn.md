# NL2SQL 多轮澄清优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化 NL2SQL 功能，实现多轮分步歧义澄清，每轮只问一个问题，用户选择后继续，直到所有歧义消除才生成 SQL。

**Architecture:** 前端维护对话历史数组 `chatHistory`，每次请求将历史传给后端。后端无状态，拼接 system prompt + 历史消息 + 当前问题发给 LLM。LLM 返回结构化 JSON（status: CLARIFY/SQL/REJECTED），前端根据 status 路由不同 UI。

**Tech Stack:** Spring AI 1.1.2 (ChatModel/Prompt), Vue 3 + TypeScript, 已有的向量检索 DDL 逻辑

---

### Task 1: 后端 DTO 改造

**Files:**
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/dto/SqlAskRequest.java`
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/dto/SqlAskResponse.java`

- [ ] **Step 1: 修改 `SqlAskRequest.java`，新增 `history` 字段和 `HistoryItem` 内部类**

```java
package com.ai.coder.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class SqlAskRequest {
    private Long knowledgeBaseId;
    private String model;
    private String question;
    private List<HistoryItem> history;

    @Data
    public static class HistoryItem {
        private String role;    // "user" | "assistant"
        private String content;
    }
}
```

- [ ] **Step 2: 修改 `SqlAskResponse.java`，新增 `status` 字段**

```java
package com.ai.coder.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlAskResponse {
    private String status;        // "SQL" | "CLARIFY" | "REJECTED"
    private String sql;
    private String clarification;
    private List<String> options;
    private Boolean isRejected;
    private Boolean canExecute;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-rag -q`
Expected: 无输出（编译成功）

- [ ] **Step 4: 提交**

```bash
git add aicoder-rag/src/main/java/com/ai/coder/rag/dto/SqlAskRequest.java aicoder-rag/src/main/java/com/ai/coder/rag/dto/SqlAskResponse.java
git commit -m "feat(rag): SqlAskRequest 新增 history 字段，SqlAskResponse 新增 status 字段"
```

---

### Task 2: 重写 System Prompt + 改造 SqlGenerationService

**Files:**
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/service/SqlGenerationService.java`

- [ ] **Step 1: 重写 `SQL_SYSTEM_PROMPT` 常量，替换整个字符串**

将 `SQL_SYSTEM_PROMPT` 替换为以下内容（完整覆盖 `SqlGenerationService.java` 中的 35-55 行）：

```java
    private static final String SQL_SYSTEM_PROMPT = """
            你是一个专业的 NL2SQL 助手。基于提供的数据库 DDL 结构，将用户的自然语言查询转换为准确的 SQL 语句。

            #核心规则
            当且仅当查询完全无歧义时，你才可以直接生成SQL；否则必须优先进行歧义澄清。

            #歧义检测标准
            当用户查询满足以下任一条件时，必须发起澄清：
            - 涉及的表名、列名存在多个可能的匹配
            - 聚合函数（COUNT/SUM/AVG等）的统计对象不明确
            - 时间范围（"最近"、"本月"、"过去"等）没有明确界定
            - 筛选条件（"高"、"低"、"优秀"等）没有量化标准
            - 排序方式（"最新"、"最热"等）没有明确排序字段
            - 多表关联关系不唯一
            - "和/或"逻辑关系不明确
            - "包含/排除"范围不明确

            #澄清问题格式要求
            - 每个澄清问题必须只针对一个歧义点
            - 每个问题必须提供2-3个最可能的答案选项
            - 选项必须互斥且覆盖绝大多数可能的情况
            - 选项必须具体、可执行，避免模糊表述

            #多轮澄清规则
            - 如果存在多个独立的歧义点，必须分轮次进行澄清，每轮只问一个问题
            - 上一轮用户选择后，再提出下一个歧义点的问题
            - 不得在一轮中同时提出多个问题
            - 澄清过程中，必须保留用户之前的所有选择信息
            - 只有当所有歧义点都被澄清后，才生成最终的SQL语句

            #最终SQL生成要求
            - 必须严格基于DDL结构和所有澄清选择生成SQL
            - 只生成SELECT查询语句，严禁INSERT/UPDATE/DELETE/DROP/ALTER/CREATE
            - SQL必须符合MySQL语法规范
            - SQL应尽量高效，避免不必要的子查询和全表扫描

            #禁止行为
            - 禁止在存在歧义时直接生成SQL
            - 禁止假设用户的意图
            - 禁止使用"可能"、"大概"等不确定的表述生成SQL
            - 禁止在澄清问题中提供超过3个选项
            - 禁止在一轮中提出多个澄清问题
            - 如果用户的问题与数据库查询完全无关，才设置status为REJECTED

            #输出格式
            请严格按以下JSON格式返回，不要包含任何其他内容：

            存在歧义时：
            {"status":"CLARIFY","clarification":"具体的一个歧义问题","options":["选项A","选项B","选项C"],"sql":null,"isRejected":false,"canExecute":false}

            无歧义、可以直接生成SQL时：
            {"status":"SQL","sql":"SELECT ...","canExecute":true,"clarification":null,"options":null,"isRejected":false}

            查询被拒绝时（与数据库查询完全无关）：
            {"status":"REJECTED","isRejected":true,"clarification":"拒绝原因","sql":null,"options":null,"canExecute":false}

            参考的数据库 DDL 结构：
            %s
            """;
```

- [ ] **Step 2: 改造 `generateSql` 方法，拼接历史消息**

将 `generateSql` 方法中构建 Prompt 的部分（第 83-86 行）替换为：

```java
        String systemPrompt = String.format(SQL_SYSTEM_PROMPT, ddlContext);

        ChatModel chatModel = resolveChatModel(request.getModel());

        List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        if (request.getHistory() != null) {
            for (SqlAskRequest.HistoryItem item : request.getHistory()) {
                if ("user".equals(item.getRole())) {
                    messages.add(new UserMessage(item.getContent()));
                } else if ("assistant".equals(item.getRole())) {
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(item.getContent()));
                }
            }
        }

        messages.add(new UserMessage(request.getQuestion()));

        Prompt prompt = new Prompt(messages);

        ChatResponse chatResponse = chatModel.call(prompt);
        String responseText = chatResponse.getResult().getOutput().getText();

        return parseSqlResponse(responseText);
```

- [ ] **Step 3: 改造 `parseSqlResponse`，兼容旧格式自动补充 status**

将 `parseSqlResponse` 方法（第 93-115 行）替换为：

```java
    private SqlAskResponse parseSqlResponse(String responseText) {
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

            SqlAskResponse response = objectMapper.readValue(json, SqlAskResponse.class);

            // 兼容：若LLM未返回status，根据其他字段自动推断
            if (response.getStatus() == null) {
                if (Boolean.TRUE.equals(response.getIsRejected())) {
                    response.setStatus("REJECTED");
                } else if (response.getSql() != null && !response.getSql().isBlank()) {
                    response.setStatus("SQL");
                } else if (response.getClarification() != null && !response.getClarification().isBlank()) {
                    response.setStatus("CLARIFY");
                } else {
                    response.setStatus("SQL");
                }
            }

            return response;
        } catch (Exception e) {
            log.warn("解析 SQL 响应失败，返回原始文本: {}", e.getMessage());
            return SqlAskResponse.builder()
                    .status("SQL")
                    .sql(responseText)
                    .clarification("")
                    .isRejected(false)
                    .canExecute(true)
                    .build();
        }
    }
```

- [ ] **Step 4: 确认需要新增的 import**

确认 `SqlGenerationService.java` 顶部有这些 import（`java.util.ArrayList` 需新增）：

```java
import com.ai.coder.rag.dto.SqlAskRequest;
import java.util.ArrayList;
```

`ArrayList` 已在文件中通过 `java.util.*` 导入，无需额外添加。`AssistantMessage` 需确认 import：

```java
import org.springframework.ai.chat.messages.AssistantMessage;
```

- [ ] **Step 5: 编译验证**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-rag -q`
Expected: 无输出（编译成功）

- [ ] **Step 6: 提交**

```bash
git add aicoder-rag/src/main/java/com/ai/coder/rag/service/SqlGenerationService.java
git commit -m "feat(rag): 重写 NL2SQL system prompt + 支持多轮历史消息"
```

---

### Task 3: 前端类型更新

**Files:**
- Modify: `aicoder-web/src/types/index.ts`

- [ ] **Step 1: 更新 `SqlAskRequest` 和 `SqlAskResponse` 接口**

将 `types/index.ts` 中的 `SqlAskRequest`（93-97 行）和 `SqlAskResponse`（99-105 行）替换为：

```typescript
export interface HistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export interface SqlAskRequest {
  knowledgeBaseId: number
  model: string
  question: string
  history?: HistoryItem[]
}

export interface SqlAskResponse {
  status: 'SQL' | 'CLARIFY' | 'REJECTED'
  sql: string | null
  clarification: string | null
  options: string[] | null
  isRejected: boolean
  canExecute: boolean
}
```

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/types/index.ts
git commit -m "feat(web): 更新 NL2SQL 相关类型定义"
```

---

### Task 4: 前端 SqlView 多轮交互改造

**Files:**
- Modify: `aicoder-web/src/views/sql/SqlView.vue`

- [ ] **Step 1: 重写 `<script setup>` 部分**

将 `<script setup>` 部分（1-90 行）替换为：

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { knowledgeApi } from '@/api/knowledge'
import { chatApi } from '@/api/chat'
import request from '@/api/request'
import type { KnowledgeBase, ModelInfo, SqlAskResponse, HistoryItem } from '@/types'

const ddlKnowledgeBases = ref<KnowledgeBase[]>([])
const models = ref<ModelInfo[]>([])
const selectedKbId = ref<number | null>(null)
const selectedModel = ref('')

const question = ref('')
const loading = ref(false)
const sqlResult = ref<SqlAskResponse | null>(null)
const executing = ref(false)
const executeResult = ref<{ columns: string[]; rows: any[][] } | null>(null)
const errorMsg = ref('')

// 多轮澄清
const chatHistory = ref<HistoryItem[]>([])
const clarifyState = ref<{ question: string; options: string[] } | null>(null)

onMounted(async () => {
  try {
    const [kbRes, modelRes] = await Promise.all([
      knowledgeApi.list(),
      chatApi.getModels()
    ])
    ddlKnowledgeBases.value = kbRes.data.filter(kb => kb.type === 'DDL')
    models.value = modelRes.data
    if (models.value.length > 0) selectedModel.value = models.value[0].modelId
  } catch {
    // ignore
  }
})

const handleAsk = async () => {
  if (!question.value.trim() || !selectedKbId.value) return
  loading.value = true
  sqlResult.value = null
  clarifyState.value = null
  executeResult.value = null
  errorMsg.value = ''

  try {
    const res = await request.post<SqlAskResponse>('/rag/sql/ask', {
      knowledgeBaseId: selectedKbId.value,
      model: selectedModel.value,
      question: question.value.trim(),
      history: chatHistory.value.length > 0 ? chatHistory.value : undefined
    })
    handleResponse(res.data)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '生成 SQL 失败'
  } finally {
    loading.value = false
  }
}

const handleResponse = (data: SqlAskResponse) => {
  if (data.status === 'CLARIFY' && data.options && data.options.length > 0) {
    clarifyState.value = { question: data.clarification || '请选择', options: data.options }
  } else if (data.status === 'REJECTED' || data.isRejected) {
    sqlResult.value = data
  } else {
    sqlResult.value = data
  }
}

const handleOptionClick = async (option: string) => {
  if (!clarifyState.value) return

  // 追加澄清历史
  chatHistory.value.push({ role: 'assistant', content: clarifyState.value.question })
  chatHistory.value.push({ role: 'user', content: option })
  clarifyState.value = null

  // 再次请求
  loading.value = true
  try {
    const res = await request.post<SqlAskResponse>('/rag/sql/ask', {
      knowledgeBaseId: selectedKbId.value,
      model: selectedModel.value,
      question: question.value.trim(),
      history: chatHistory.value
    })
    handleResponse(res.data)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '生成 SQL 失败'
  } finally {
    loading.value = false
  }
}

const handleNewQuestion = () => {
  chatHistory.value = []
  clarifyState.value = null
  sqlResult.value = null
  executeResult.value = null
  errorMsg.value = ''
}

const handleExecute = async () => {
  if (!sqlResult.value?.sql) return
  executing.value = true
  errorMsg.value = ''

  const kb = ddlKnowledgeBases.value.find(k => k.id === selectedKbId.value)
  try {
    const res = await request.post('/rag/sql/execute', {
      sql: sqlResult.value.sql,
      databaseName: kb?.databaseName || '',
      jdbcUrl: kb?.jdbcUrl || '',
      dbUsername: kb?.dbUsername || '',
      dbPassword: kb?.dbPassword || ''
    })
    const data = res.data
    if (data.columns && data.rows) {
      executeResult.value = data
    } else {
      errorMsg.value = '执行结果格式异常'
    }
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || 'SQL 执行失败'
  } finally {
    executing.value = false
  }
}

const copySql = (sql: string) => {
  navigator.clipboard.writeText(sql)
}
</script>
```

- [ ] **Step 2: 重写 `<template>` 部分**

将 `<template>` 部分（92-189 行）替换为：

```html
<template>
  <div class="sql-view">
    <div class="config-panel">
      <h3>NL2SQL 配置</h3>
      <div class="form-group">
        <label>DDL 知识库</label>
        <select v-model="selectedKbId">
          <option :value="null" disabled>请选择 DDL 知识库</option>
          <option v-for="kb in ddlKnowledgeBases" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
        </select>
      </div>
      <div class="form-group">
        <label>模型</label>
        <select v-model="selectedModel">
          <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.modelName }}</option>
        </select>
      </div>
    </div>

    <div class="main-panel">
      <div class="input-section">
        <div class="form-group">
          <label>自然语言查询</label>
          <textarea
            v-model="question"
            rows="3"
            placeholder="请输入自然语言问题，例如：查询最近7天的销售总额"
            @keydown.enter.ctrl="handleAsk"
          />
        </div>
        <div class="input-actions">
          <button
            class="btn btn-primary"
            :disabled="!question.trim() || !selectedKbId || loading"
            @click="handleAsk"
          >
            {{ loading ? '思考中...' : '生成 SQL' }}
          </button>
          <button
            v-if="chatHistory.length > 0"
            class="btn btn-secondary"
            @click="handleNewQuestion"
          >重新提问</button>
        </div>
      </div>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <!-- 多轮澄清对话区 -->
      <div v-if="chatHistory.length > 0 || clarifyState" class="chat-section">
        <div class="chat-messages">
          <template v-for="(msg, idx) in chatHistory" :key="idx">
            <div :class="['chat-bubble', msg.role === 'user' ? 'bubble-user' : 'bubble-assistant']">
              <span class="bubble-icon">{{ msg.role === 'user' ? '👤' : '🤖' }}</span>
              <span class="bubble-text">{{ msg.content }}</span>
            </div>
          </template>
          <!-- 当前澄清 -->
          <div v-if="clarifyState" class="chat-bubble bubble-clarify">
            <span class="bubble-icon">🤖</span>
            <div class="clarify-content">
              <p class="clarify-question">{{ clarifyState.question }}</p>
              <div class="clarify-options">
                <button
                  v-for="(opt, i) in clarifyState.options"
                  :key="i"
                  class="btn btn-clarify-option"
                  :disabled="loading"
                  @click="handleOptionClick(opt)"
                >{{ opt }}</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="sqlResult" class="result-section">
        <div v-if="sqlResult.isRejected" class="rejected-card">
          <h4>请求被拒绝</h4>
          <p>{{ sqlResult.clarification || '该查询被系统拒绝，请调整问题后重试。' }}</p>
        </div>

        <div v-if="sqlResult.sql" class="sql-card">
          <h4>生成的 SQL</h4>
          <pre class="sql-code"><code>{{ sqlResult.sql }}</code></pre>
          <div class="sql-actions">
            <button
              v-if="sqlResult.canExecute"
              class="btn btn-execute"
              :disabled="executing"
              @click="handleExecute"
            >
              <span class="execute-icon">▶</span>
              {{ executing ? '执行中...' : '执行 SQL' }}
            </button>
            <button
              class="btn btn-secondary btn-sm"
              @click="copySql(sqlResult.sql)"
            >复制</button>
          </div>
        </div>

        <div v-if="executeResult" class="table-card">
          <h4>执行结果</h4>
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th v-for="col in executeResult.columns" :key="col">{{ col }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in executeResult.rows" :key="ri">
                  <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="result-info">共 {{ executeResult.rows.length }} 条记录</p>
        </div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 3: 重写 `<style>` 部分**

将 `<style scoped lang="scss">` 部分（193-322 行）替换为（保留原有样式，新增对话区样式）：

```scss
<style scoped lang="scss">
.sql-view {
  height: 100%;
  display: flex;
  gap: 20px;
}

.config-panel {
  width: 280px;
  flex-shrink: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;

  h3 {
    font-family: 'Sora', sans-serif;
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 20px;
    color: var(--text-primary);
  }
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.input-section {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;
}

.input-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.error-msg {
  background: var(--surface-danger);
  border: 1px solid rgba(196, 86, 74, 0.25);
  color: var(--danger);
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

// 多轮澄清对话区
.chat-section {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-bubble {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 85%;
}

.bubble-icon {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble-assistant {
  align-self: flex-start;
}

.bubble-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.bubble-text {
  padding: 8px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}

.bubble-assistant .bubble-text {
  background: var(--bg-overlay);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

.bubble-user .bubble-text {
  background: #3b82f6;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.bubble-clarify {
  align-self: flex-start;
  max-width: 90%;
}

.clarify-content {
  padding: 12px 16px;
  background: var(--bg-overlay);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  border-bottom-left-radius: 4px;
}

.clarify-question {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.clarify-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.btn-clarify-option {
  padding: 6px 16px;
  font-size: 13px;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover:not(:disabled) {
    background: rgba(59, 130, 246, 0.2);
    border-color: rgba(59, 130, 246, 0.5);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.result-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rejected-card, .sql-card, .table-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;

  h4 {
    font-family: 'Sora', sans-serif;
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 12px;
    color: var(--text-primary);
  }
}

.rejected-card p {
  color: var(--danger);
  font-size: 14px;
}

.sql-code {
  background: #1e293b;
  color: #e2e8f0;
  padding: 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 12px;
}

.sql-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-execute {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.3);

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #16a34a 0%, #15803d 100%);
    box-shadow: 0 4px 12px rgba(34, 197, 94, 0.45);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.execute-icon {
  font-size: 10px;
}

.table-wrapper {
  overflow-x: auto;
  margin-bottom: 12px;

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;

    th, td {
      border: 1px solid var(--border-default);
      padding: 8px 12px;
      text-align: left;
      white-space: nowrap;
    }
    th {
      background: var(--bg-overlay);
      font-weight: 600;
      color: var(--text-primary);
    }
    td { color: var(--text-primary); }
  }
}

.result-info {
  font-size: 13px;
  color: var(--text-muted);
}
</style>
```

- [ ] **Step 4: 提交**

```bash
git add aicoder-web/src/views/sql/SqlView.vue
git commit -m "feat(web): NL2SQL 多轮澄清交互 UI"
```

---

### Task 5: 编译验证 + 重启 rag 服务测试

**Files:** 无新文件

- [ ] **Step 1: 全量编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -q`
Expected: 无输出（编译成功）

- [ ] **Step 2: 重启 rag 服务**

Run: `lsof -i :8083 -t | xargs kill && sleep 2 && nohup mvn spring-boot:run -pl aicoder-rag -q > /tmp/aicoder-rag.log 2>&1 &`
等待约 40 秒后验证启动成功：`grep "Started" /tmp/aicoder-rag.log | tail -1`

- [ ] **Step 3: 测试无歧义场景**

```bash
curl -s -X POST http://localhost:8083/api/rag/sql/ask -H "Content-Type: application/json" -d '{"knowledgeBaseId":11,"model":"deepseek-v4-flash","question":"查询所有表名"}'
```
Expected: 返回 `{"status":"SQL","sql":"SELECT ...","canExecute":true,...}`

- [ ] **Step 4: 测试歧义场景**

```bash
curl -s -X POST http://localhost:8083/api/rag/sql/ask -H "Content-Type: application/json" -d '{"knowledgeBaseId":11,"model":"deepseek-v4-flash","question":"查询最近的销售数据"}'
```
Expected: 返回 `{"status":"CLARIFY","clarification":"...","options":["...","...","..."],...}`

- [ ] **Step 5: 测试多轮澄清**

将上一步返回的选项通过 history 传入：

```bash
curl -s -X POST http://localhost:8083/api/rag/sql/ask -H "Content-Type: application/json" -d '{"knowledgeBaseId":11,"model":"deepseek-v4-flash","question":"查询最近的销售数据","history":[{"role":"assistant","content":"您指的最近是哪个时间范围？"},{"role":"user","content":"最近7天"}]}'
```
Expected: 若仍有歧义返回 `CLARIFY`，否则返回 `SQL`

- [ ] **Step 6: 提交（如有修复）**

```bash
git add -A && git commit -m "fix: NL2SQL 多轮澄清验证修复"  # 仅在有修复时执行
```

- [ ] **Step 7: 创建 tag**

```bash
git tag v1.2.0 -m "feat: NL2SQL 多轮歧义澄清优化"
```
