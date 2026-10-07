# NL2SQL 多轮澄清优化 — 设计文档

日期：2026-06-05

## 目标

优化 NL2SQL 功能，实现多轮分步歧义澄清：当用户查询存在歧义时，每轮只问一个问题、提供 2-3 个选项，用户选择后继续下一轮澄清，直到所有歧义消除后才生成最终 SQL。

## 决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| 澄清方式 | 前端多轮交互 | 前端维护对话历史，后端无状态，灵活且易扩展 |
| 会话管理 | 前端维护 history 数组 | 无需后端 session/Redis，简化架构 |
| LLM 输出 | 结构化 JSON + status 字段 | 前端根据 status 路由不同 UI |

---

## 一、后端改动

### 1.1 `SqlAskRequest` 新增 `history` 字段

```java
@Data
public class SqlAskRequest {
    private Long knowledgeBaseId;
    private String model;
    private String question;
    private List<HistoryItem> history;
}
```

新增 `HistoryItem` 内部类：

```java
@Data
public static class HistoryItem {
    private String role;    // "user" | "assistant"
    private String content; // 消息内容
}
```

### 1.2 `SqlAskResponse` 新增 `status` 字段

```java
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

### 1.3 System Prompt 重写

按用户提供的 5 条核心规则重写 `SQL_SYSTEM_PROMPT`，关键约束：

```
#核心任务
基于DDL结构，将自然语言查询转换为准确的SQL语句。当且仅当查询完全无歧义时，你才可以直接生成SQL；否则必须优先进行歧义澄清。

#歧义检测标准
- 涉及的表名、列名存在多个可能的匹配
- 聚合函数的统计对象不明确
- 时间范围没有明确界定
- 筛选条件没有量化标准
- 排序方式没有明确排序字段
- 多表关联关系不唯一
- "和/或"逻辑关系不明确
- "包含/排除"范围不明确

#澄清规则
- 每轮只针对一个歧义点提问
- 提供2-3个互斥且具体的选项
- 不得假设用户意图

#输出格式（严格JSON）
存在歧义时：
{"status":"CLARIFY","clarification":"具体问题","options":["选项A","选项B","选项C"],"sql":null,"isRejected":false,"canExecute":false}

无歧义时：
{"status":"SQL","sql":"SELECT ...","canExecute":true,"clarification":null,"options":null,"isRejected":false}

拒绝时：
{"status":"REJECTED","isRejected":true,"clarification":"拒绝原因","sql":null,"options":null,"canExecute":false}
```

### 1.4 `SqlGenerationService.generateSql` 改造

1. 拼接消息：`SystemMessage` + 遍历 `history` 构建 `UserMessage/AssistantMessage` + 当前 `UserMessage`
2. 其余逻辑不变（向量检索 DDL、模型选择、JSON 解析）

### 1.5 `parseSqlResponse` 兼容旧格式

- 解析 JSON 时，若无 `status` 字段但有 `sql` 字段，自动设置 `status = "SQL"`
- 若有 `clarification` 但无 `sql`，自动设置 `status = "CLARIFY"`

---

## 二、前端改动

### 2.1 新增状态

```typescript
const chatHistory = ref<{role: string, content: string}[]>([])
const clarifyState = ref<{question: string, options: string[]} | null>(null)
```

### 2.2 `handleAsk` 改造

- 调用 `/rag/sql/ask` 时传入 `chatHistory`
- 收到响应后根据 `status` 路由：
  - `CLARIFY` → 设置 `clarifyState`，展示澄清气泡
  - `SQL` → 清除 `clarifyState`，展示 SQL 卡片
  - `REJECTED` → 展示拒绝信息

### 2.3 `handleOptionClick` 改造

用户点击选项后：
1. 将上一轮 assistant 的澄清问题 + 用户选择的选项追加到 `chatHistory`
2. 清除 `clarifyState`
3. 再次调用 `handleAsk`（此时 question 不变，history 已更新）

### 2.4 UI 结构

```
┌─────────────────────────────────┐
│ 配置面板（DDL知识库 + 模型选择）    │
├─────────────────────────────────┤
│ 输入框 + 生成 SQL 按钮            │
├─────────────────────────────────┤
│ 对话区（多轮澄清气泡）             │
│  🤖 您指的"最近"是哪个时间范围？    │
│     [最近7天] [最近30天] [本月]    │
│  👤 最近7天                       │
│  🤖 "高销售额"的标准是？           │
│     [>10000] [>50000] [>100000]  │
│  👤 >10000                       │
├─────────────────────────────────┤
│ SQL 结果卡片 + 执行/复制按钮       │
├─────────────────────────────────┤
│ 执行结果表格                      │
└─────────────────────────────────┘
```

每次新提问时清空 `chatHistory`，重新开始。

### 2.5 错误处理

不变，保持现有的 `errorMsg` 机制。

---

## 三、文件变更清单

### 后端修改
- `dto/SqlAskRequest.java` — 新增 `history` 字段和 `HistoryItem` 内部类
- `dto/SqlAskResponse.java` — 新增 `status` 字段
- `service/SqlGenerationService.java` — 重写 system prompt，拼接历史消息

### 前端修改
- `views/sql/SqlView.vue` — 新增对话历史状态，改造交互流程和 UI
- `types/index.ts` — `SqlAskResponse` 增加 `status` 字段，新增 `HistoryItem` 类型

---

## 四、不改动

- `SqlController` — 路由和接口不变
- `SqlExecutionService` — SQL 执行逻辑不变
- 向量检索 DDL 的逻辑不变
- 模型选择逻辑不变
