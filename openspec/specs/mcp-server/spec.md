---
title: MCP Server（aicoder-mcp 模块）
description: 把平台能力（计算器、网络搜索、图文解析、技能读写）封装为标准 MCP Server，以 SSE 协议（GET /sse + POST /mcp/message?sessionId=xxx）经 Gateway /api/mcp/** 暴露给外部 MCP 客户端（Claude Desktop / Cursor）
status: active
---

## Purpose

平台现有工具调用（chat 的 @Tool 与手写 ReAct Agent）全部硬编码在 chat 模块、无统一管理。新增 `aicoder-mcp` MCP Server 微服务（端口 8087），将平台能力封装为 MCP 工具暴露。

## Requirements

### Requirement: The system SHALL provide a calculator tool
The calculator tool SHALL evaluate mathematical expressions and return numeric results, supporting basic operators (+ - * / %) and parentheses.

#### Scenario: 基本算术求值
- **WHEN** 客户端调用 `calculator("3 * (2 + 2)")`
- **THEN** 返回 `"12"`（整数去小数点）

#### Scenario: 小数除法的结果
- **WHEN** 客户端调用 `calculator("10 / 4")`
- **THEN** 返回 `"2.5"`（保留小数）

#### Scenario: 非法表达式拒绝
- **WHEN** 客户端调用 `calculator("1 + )")` 或 `calculator("foo")`
- **THEN** 返回友好错误提示（不抛栈）

#### Scenario: 超长表达式拒绝
- **WHEN** 表达式超过 500 字符
- **THEN** 拒绝计算，返回长度超限提示

### Requirement: The system SHALL provide web search and content extraction
Web search SHALL query an external search API (Tavily) and web_fetch SHALL fetch HTML content and convert it to readable markdown.

#### Scenario: 网络搜索成功
- **WHEN** 客户端调用 `web_search("spring ai")`，且已配置 Tavily API key
- **THEN** 返回若干条结果（标题 + 链接 + 摘要）

#### Scenario: 搜索未配置 key
- **WHEN** API key 为空或未配置
- **THEN** 返回"网络搜索未配置"提示

#### Scenario: 网页抓取转 markdown
- **WHEN** 客户端调用 `web_fetch("https://example.com")`
- **THEN** 抓取 HTML 正文，经 Jsoup 清洗 + flexmark 转换为易读 markdown 文档

#### Scenario: SSRF 防护——拒绝内网地址
- **WHEN** 目标 URL 为 localhost、127.0.0.1、192.168.x 等内网地址
- **THEN** 拒绝抓取并抛出异常

### Requirement: The system SHALL provide image analysis via multimodal LLM
image_analysis SHALL call a multimodal model (from ModelConfig) to analyze image content.

#### Scenario: 图片解析成功
- **WHEN** 客户端调用 `image_analysis("https://example.com/cat.png", "描述图片")`，且 DB 中已配置多模态模型
- **THEN** 返回模型解析文本（如"图中是一只猫"）

#### Scenario: 模型未配置或不可用
- **WHEN** 多模态模型缺失或调用失败
- **THEN** 返回友好错误提示，不抛栈

### Requirement: The system SHALL provide skill read/write capabilities
read_skill SHALL fetch skill content via aicoder-skill; submit_skill_draft SHALL submit drafts with X-User-Id header propagation.

#### Scenario: 读取已有技能
- **WHEN** 客户端调用 `read_skill("code-review")`，且技能库中存在该技能
- **THEN** 返回技能名称和完整内容

#### Scenario: 提交技能草稿
- **WHEN** 客户端调用 `submit_skill_draft("code-review", "审查", "content")`
- **THEN** POST 到 aicoder-skill 服务，返回提交状态（PENDING_APPROVAL 等）；X-User-Id 头透传服务账号

### Requirement: The system SHALL authenticate MCP endpoints via X-Mcp-Token
MCP endpoints SHALL verify X-Mcp-Token before processing. Gateway JWT filter SHALL whitelist /api/mcp/**.

#### Scenario: Token 正确
- **WHEN** 请求携带 `X-Mcp-Token` 匹配配置值
- **THEN** 通过鉴权，正常处理

#### Scenario: Token 错误或缺失
- **WHEN** Token 不对、为空、或缺失
- **THEN** 返回 HTTP 401

#### Scenario: 非 MCP 路径放行
- **WHEN** 请求路径不含 /mcp
- **THEN** 跳过 MCP 鉴权，不做拦截

### Requirement: The system SHALL use SSE transport protocol
Spring AI 1.1.2 `type:SYNC` activates `WebMvcSseServerTransportProvider`. The SSE endpoint is `GET /sse` (returns sessionId) and the message endpoint is `POST /mcp/message?sessionId=<uuid>` (JSON-RPC). Clients SHALL first GET /sse to obtain a sessionId, then POST JSON-RPC messages to /mcp/message with that sessionId.

#### Scenario: SSE session established
- **WHEN** client sends GET /sse with valid X-Mcp-Token
- **THEN** response is `event:endpoint` with `data:/mcp/message?sessionId=<uuid>`

#### Scenario: JSON-RPC initialize via SSE
- **WHEN** client POSTs `initialize` JSON-RPC to /mcp/message?sessionId=<uuid>
- **THEN** server responds via SSE channel with protocol version and capabilities (tools/resources/prompts/completions)

### Requirement: The system SHALL route /api/mcp/** via Gateway
Gateway SHALL forward /api/mcp/** requests to lb://aicoder-mcp. The MCP SSE endpoint is `/sse` (GET for session connection) and `/mcp/message` (POST for JSON-RPC).

#### Scenario: SSE 连接获取 sessionId
- **WHEN** 客户端 GET `http://<网关>:8080/api/mcp/sse`（或直连 `http://<mcp-host>:8087/sse`）
- **THEN** 返回 SSE event:endpoint 包含 `data:/mcp/message?sessionId=<uuid>`

#### Scenario: JSON-RPC 消息请求
- **WHEN** 客户端 POST `/mcp/message?sessionId=<uuid>` 携带 JSON-RPC body
- **THEN** MCP server 处理并通过 SSE 通道返回响应

### Requirement: The system SHALL expose a getByName endpoint on skill service
skill service SHALL provide GET /api/skill/name/{name} for MCP read_skill.

#### Scenario: 按 name 查技能
- **WHEN** 调用 `GET /api/skill/name/{name}`
- **THEN** 返回对应技能实体；不存在时返回 404

### Requirement: The system SHALL reuse ModelConfig for multimodal models
Multimodal models SHALL be read from ModelConfig (no new DB tables). Nacos config SHALL support hot refresh.

#### Scenario: 模型配置变更
- **WHEN** DB 中新增或修改多模态模型配置
- **THEN** McpDynamicModelRegistry 通过 reload 可热刷新获取新的 ChatModel

### Requirement: The system SHALL apply timeout on external HTTP calls
plainRestTemplate SHALL apply connect/read timeout from McpProperties.fetch to prevent hanging on external URLs.

#### Scenario: 超时生效
- **WHEN** 外部 URL 无响应超过配置时间
- **THEN** RestTemplate 超时抛出异常，被工具层兜底为友好错误提示
