---
title: NL2SQL 多轮歧义澄清优化
description: 优化 NL2SQL 功能，实现前端多轮分步歧义澄清——每轮只问一个问题、提供 2-3 个选项，歧义消除后再生成最终 SQL
status: active
---

## Purpose

当用户自然语言查询存在歧义时（表名多匹配、时间范围不明确、筛选条件无量纲等），原实现缺少多轮澄清机制。本 spec 改造前后端，使 NL2SQL 支持多轮分步歧义澄清：后端 LLM 按结构化 JSON 标识状态，前端根据 status 路由不同 UI。

## Requirements

### Requirement: The system SHALL support multi-turn clarification via history field
`SqlAskRequest` SHALL 新增 `history: List<HistoryItem>` 字段，`HistoryItem` 含 `role`（"user"|"assistant"）和 `content`，前端每轮将澄清对话历史传入后端，后端拼接为完整消息列表送 LLM，实现无状态多轮交互。

#### Scenario: 首轮直接生成 SQL
- **WHEN** 用户首次提问 "查询上个月销售额最高的商品" 且语义无歧义
- **THEN** 后端返回 `{"status":"SQL","sql":"SELECT ...","canExecute":true}`

#### Scenario: 多轮澄清后生成 SQL
- **WHEN** 第一轮 LLM 返回 `CLARIFY` 且 options=["最近7天","最近30天","本月"]，用户选择"最近30天"后第二轮再次调用接口并传入 history
- **THEN** 后端拼接完整的 system + history + 当前 question，LLM 消歧后返回 `{"status":"SQL","sql":"SELECT ..."}`

### Requirement: The system SHALL add status field to SqlAskResponse for UI routing
`SqlAskResponse` SHALL 新增 `status` 字段，取值 "SQL"|"CLARIFY"|"REJECTED"，前端根据 status 路由不同 UI 展示。

#### Scenario: CLARIFY 状态展示澄清气泡
- **WHEN** 后端返回 `{"status":"CLARIFY","clarification":"您指的最近是哪个时间范围？","options":["最近7天","最近30天","本月"]}`
- **THEN** 前端设置 `clarifyState`，展示澄清问题与 2-3 个可点击选项按钮

#### Scenario: SQL 状态展示结果卡片
- **WHEN** 后端返回 `{"status":"SQL","sql":"SELECT ...","canExecute":true}`
- **THEN** 前端清除 `clarifyState`，展示 SQL 代码卡片及执行/复制按钮

#### Scenario: REJECTED 状态展示拒绝信息
- **WHEN** 后端返回 `{"status":"REJECTED","clarification":"查询超出知识库范围","isRejected":true}`
- **THEN** 前端展示拒绝原因文本

### Requirement: The system SHALL rewrite the system prompt for structured clarification
`SQL_SYSTEM_PROMPT` SHALL 重写以涵盖歧义检测标准（表名列名多匹配、聚合对象不明确、时间范围无界定、筛选无量纲、排序字段缺失、关联不唯一、逻辑关系不清、包含排除不明），每轮只针对一个歧义点提供 2-3 个互斥具体选项，严格输出 JSON。

#### Scenario: 歧义检测——时间范围不清
- **WHEN** 用户提问 "最近的订单"
- **THEN** LLM 检测到时间范围无明确界定，返回 `CLARIFY` 状态及具体时间选项

#### Scenario: 歧义检测——聚合对象不明确
- **WHEN** 用户提问 "销售额最高的"
- **THEN** LLM 检测到聚合函数的统计对象存在多可能，返回 `CLARIFY` 状态要求选择统计维度

#### Scenario: 多歧义时分步澄清
- **WHEN** 同一查询存在多个歧义点
- **THEN** LLM 每轮只输出一个 CLARIFY（针对最先检测到的歧义），下一轮继续下一个，直到全部消除

### Requirement: The system SHALL maintain dialog history on the frontend
前端 SHALL 维护 `chatHistory: ref<{role: string, content: string}[]>` 数组，用户点击选项后将上一轮 assistant 澄清问题 + 用户选择的选项追加到 history，清除 `clarifyState`，再次调用 `handleAsk`。

#### Scenario: 用户选择选项后继续
- **WHEN** 用户在选项按钮上点击"最近30天"
- **THEN** 将 assistant 的澄清问题与 user 的选项追加到 `chatHistory`，再次调用 `/rag/sql/ask` 接口并传入更新后的 history

#### Scenario: 新提问清空历史
- **WHEN** 用户输入新问题
- **THEN** `chatHistory` 清空重新开始

### Requirement: The system SHALL be backward compatible with old response format
`parseSqlResponse` SHALL 兼容旧格式：无 `status` 字段但有 `sql` 则自动设 `status="SQL"`；有 `clarification` 但无 `sql` 则自动设 `status="CLARIFY"`。

#### Scenario: 旧格式兼容
- **WHEN** LLM 返回不含 `status` 字段的旧格式 JSON `{"sql":"SELECT ...","canExecute":true}`
- **THEN** 解析后 `status` 自动设为 "SQL"，前端正常展示
