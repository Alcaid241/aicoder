# aicoder-mcp 模块 — MCP Server 设计

> **动机**：平台现有工具调用（chat 的 `@Tool` 与手写 ReAct Agent）全部硬编码在 chat 模块、零外部可复用、无统一管理；同时外部 MCP 客户端（Claude Desktop / Cursor）无法接入平台能力。本设计新增 `aicoder-mcp` 微服务，作为**标准 MCP Server**，把平台能力（计算器、网络搜索与信息提取、图文解析、技能读写）封装为 MCP 工具暴露，供外部 MCP 客户端经 SSE 接入调用（实际：Spring AI 1.1.2 `type:SYNC` → SSE 模式，端点 GET /sse + POST /mcp/message?sessionId=xxx）。

## 1. 范围（已与用户确认）

- **目标**：新增 `aicoder-mcp` MCP Server 微服务（端口 8087），对外暴露 4 类工具。
- **第一版工具**：
  - `calculator`（计算器）
  - `web_search`（网络搜索）+ `web_fetch`（网页抓取 → 易读 markdown）
  - `image_analysis`（图文解析，多模态 LLM）
  - `read_skill` / `submit_skill_draft`（技能读写，调 aicoder-skill）
- **不做**：代码运行工具（沙箱，后续迭代）；MCP Client（聚合外部 server，另一目标）；管理前端（4 类工具固定，价值低）；新建 DB 表（复用 ModelConfig）。
- **传输协议**：WebMVC SSE（starter: `spring-ai-starter-mcp-server-webmvc`；`type: SYNC` 实际触发 SSE 模式，非 streamable-http）。端点：`GET /sse`（建立连接、获取 sessionId）+ `POST /mcp/message?sessionId=xxx`（JSON-RPC 请求）。
- **认证**：服务账号 + 静态 access token（第一版简化）。

## 2. 现状（勘察事实）

| 项 | 事实 |
|---|---|
| MCP 代码 | **无**（零 MCP server/client，pom 未引 `spring-ai-mcp`） |
| 现有工具调用 | 全在 chat，两套硬编码：`@Tool`（`read_skill`/`submit_skill_draft`，`ChatService.java:60` `.defaultTools`）+ 手写 ReAct（`knowledge_search`/`sql_query`/`http_request`） |
| rag/workflow 工具 | 无 |
| 可复用范式 | `aicoder-core`（`AbstractDynamicModelRegistry` + `RegistryReloadController` 共享 controller）；`aicoder-skill`（独立微服务管理面范式） |
| 模型配置 | `ModelConfig`(@Table `ai_model_config`) + `ModelProvider`，DB 驱动 + Nacos 热重载；当前未确认已配多模态模型 |
| Gateway 路由 | `application.yml` 声明式 `lb://<服务>`，现有 6 条（admin/chat/rag/workflow/system/skill） |
| JWT 鉴权 | Gateway `JwtAuthFilter`(GlobalFilter)，白名单路径跳过校验 |

## 3. 架构

```
外部 MCP 客户端 (Claude Desktop / Cursor)
        │  SSE (GET /sse → sessionId, POST /mcp/message?sessionId=xxx 发送 MCP JSON-RPC)
        ▼
Gateway :8080  ── /api/mcp/** (lb://aicoder-mcp, JWT 白名单放行) ──►  aicoder-mcp :8087
                                                                    (MCP Server, webmvc starter)
                                                                        │  @Tool 方法
                                                                        ├── calculator        (本地 exp4j)
                                                                        ├── web_search        (外部搜索 API)
                                                                        ├── web_fetch         (Jsoup+Tika 本地抓取)
                                                                        ├── image_analysis    (多模态 LLM ← ModelConfig)
                                                                        └── read_skill/submit (RestTemplate lb://aicoder-skill)
```

**关键性质**：`aicoder-mcp` 是独立微服务，在自己 JVM 内实现工具逻辑；技能读写、图文解析分别通过 HTTP 调 `aicoder-skill` 与多模态模型，**不引入对 chat 的代码依赖**（chat 是服务非 jar）。

## 4. 组件

### 4.1 模块骨架（仿 aicoder-skill 范式）

```
aicoder-mcp/  (com.ai.coder.mcp)
  pom.xml
  AicoderMcpApplication.java        @SpringBootApplication @EnableDiscoveryClient @ConfigurationPropertiesScan
  config/
    McpServerConfig.java            MCP server 元信息 + ToolCallbackProvider bean（汇总 @Tool）
    McpProperties.java              @ConfigurationProperties("mcp") @RefreshScope
    McpAuthFilter.java              校验 MCP access token（servlet filter）
  tool/
    CalculatorTool.java             @Tool calculator(expression)
    WebSearchTool.java              @Tool web_search(query, maxResults?)
    WebFetchTool.java               @Tool web_fetch(url)
    ImageAnalysisTool.java          @Tool image_analysis(image, question)
    SkillTool.java                  @Tool read_skill(name) / submit_skill_draft(name, description, content)
  service/
    WebSearchService.java           调外部搜索 API
    WebFetchService.java            抓取 + HTML → markdown
    ImageAnalysisService.java       调多模态 ChatModel
  exception/GlobalExceptionHandler.java
  resources/application.yml, bootstrap.yml
```

**pom 依赖**（版本由 `spring-ai-bom` 1.1.2 管理，无需写 version）：

- `aicoder-core`（传递带入 Ollama/DeepSeek starter + `ModelConfig` 注册表，供图文解析取多模态模型）
- `spring-ai-starter-mcp-server-webmvc`（Maven Central 1.1.2 可用，auto-configuration）
- `spring-cloud-starter-alibaba-nacos-discovery` / `nacos-config`
- `spring-cloud-starter-loadbalancer`（`@LoadBalanced` 调 aicoder-skill）
- `spring-boot-starter-web`（webmvc）
- `jsoup`（web_fetch 抓取 HTML 正文）+ `flexmark`（HTML → markdown 转换模块，输出易读 markdown）
- `exp4j`（calculator 表达式求值）
- `springdoc-openapi-starter-webmvc-ui`

### 4.2 各工具契约

| 工具 | 入参 | 出参 | 实现 |
|---|---|---|---|
| `calculator` | `expression: String` | 数值结果 | exp4j 求值；限长度、禁危险函数 |
| `web_search` | `query: String`, `maxResults?: int` | `[{title, snippet, url}]` | 外部搜索 API（Tavily / 阿里百炼联网搜索），key 配 Nacos |
| `web_fetch` | `url: String` | markdown `String` | Jsoup 抓取正文 HTML → flexmark 转 markdown；**SSRF 防护** |
| `image_analysis` | `image: String`(url/base64), `question: String` | 解析文本 | 多模态 ChatModel（从 ModelConfig 取，如 Ollama llava / qwen-vl） |
| `read_skill` | `name: String` | 技能内容 | `GET lb://aicoder-skill/api/skill/{name}` |
| `submit_skill_draft` | `name, description, content` | 草稿 id | `POST lb://aicoder-skill/api/skill/draft` |

### 4.3 工具暴露机制

每个工具类是 `@Component`，方法标 `@Tool`；`McpServerConfig` 用 `ToolCallbacks.from(...)` 把工具 bean 聚合为 `ToolCallbackProvider` `@Bean`；`spring-ai-starter-mcp-server-webmvc` 自动配置捕获该 provider，把工具暴露为 MCP tools，经 SSE 端点提供（Spring AI 1.1.2 `type:SYNC` → `WebMvcSseServerTransportProvider`）。端点实际为 `GET /sse`（SSE 连接）+ `POST /mcp/message?sessionId=xxx`（JSON-RPC），经 javap + 实测验证。

## 5. 传输协议与端点

- **协议模式**：SSE（Server-Sent Events）。`type: SYNC` 在 Spring AI 1.1.2 实际触发 `WebMvcSseServerTransportProvider`（经 javap + 实测验证）。
- **starter**：`spring-ai-starter-mcp-server-webmvc`（1.1.2，BOM 管版本）。
- **MCP SSE 端点**（配置 `spring.ai.mcp.server.*` 的 `name` / `version` / `type=SYNC`）：
  - `GET /sse` — SSE 连接端点，返回 `event:endpoint` + `data:/mcp/message?sessionId=<uuid>`
  - `POST /mcp/message?sessionId=<uuid>` — JSON-RPC 消息端点（MCP 协议请求/响应）
- **Gateway 路由**：新增 `id=mcp-service`, `uri=lb://aicoder-mcp`, `predicates: Path=/api/mcp/**`（直连时也可不经 Gateway 直接用 8087 端口）。
- **外部客户端接入 URL**：
  - 直连：`http://<mcp-host>:8087/sse`
  - 经 Gateway：`http://<gateway-host>:8080/api/mcp/sse`
  - Claude Desktop 配置示例：`"url": "http://<host>:8087/sse", "headers": { "X-Mcp-Token": "xxx" }`

## 6. 认证

- 外部客户端在请求头携带 MCP access token（如 `X-Mcp-Token`）。
- `McpAuthFilter`（mcp 模块内 servlet filter）校验 token == Nacos 配置的 `mcp.access-token`，不通过返回 401。
- Gateway `JwtAuthFilter` 白名单加 `/api/mcp/**`（放行 JWT，由 mcp 自己的 filter 校验 MCP token）。
- **身份映射**：所有调用归属一个服务账号 `mcp.service-user-id`（Nacos 配置）；技能读写等记录到该账号（`X-User-Id` 透传）。第一版不支持 per-client 用户映射（后续扩展）。

## 7. 配置（DB + Nacos）

- **DB**：复用 `ai_model_config` / `ai_model_provider`（图文解析取多模态模型）。**第一版不新建表**。
- **Nacos**（`mcp.*`，`@RefreshScope` 热刷新）：
  - `mcp.access-token`
  - `mcp.service-user-id`
  - `mcp.search.provider` / `mcp.search.api-key` / `mcp.search.max-results`
  - `mcp.fetch.connect-timeout` / `read-timeout` / `max-length` / `allow-private-ip`(false)
  - `mcp.image.model-code`（多模态模型 code，从 ModelConfig 取）
  - `spring.ai.mcp.server.name` / `version` / `type`

## 8. 前端

**第一版不做管理前端**。后续可加只读「工具列表 + 客户端接入配置说明」页（仿 `SkillManageView.vue` 范式，router 加 `mcp` 路由）。

## 9. 实现步骤（顺序）

1. 建 `aicoder-mcp` 骨架（pom + Application + `application.yml`/`bootstrap.yml`，端口 8087，Nacos 注册），父 pom 加 module；mvn 编译通过。
2. 加 Gateway 路由 + `JwtAuthFilter` 白名单 `/api/mcp/**`。
3. 引 `spring-ai-starter-mcp-server-webmvc`，配 `McpServerConfig`（`ToolCallbackProvider`）+ 一个最小 `@Tool`（calculator），验证 MCP server 启动、Claude Desktop 能发现该工具。
4. 加 `McpProperties` + `McpAuthFilter`（token 校验）。
5. 逐个实现工具：calculator → web_search → web_fetch → image_analysis → read_skill/submit_skill_draft。每个工具单测 + 手动调用验证。
6. image_analysis 前置：确认 ModelConfig 有多模态模型；若无，配一个（如 Ollama llava / qwen-vl）。
7. 端到端：Claude Desktop 接入，4 类工具全部可调用返回正确结果。

每步独立可编译可验证，出问题可定位到具体工具。

## 10. 测试 / 验收

- **编译**：`aicoder-mcp` + 父聚合全绿。
- **启动**：mcp 注册到 Nacos，日志见 MCP server started。
- **工具单测**：calculator 各类表达式 + 异常输入；web_search mock 搜索 API；web_fetch 抓取固定 URL 断言含正文 + SSRF 拒绝内网地址；image_analysis mock ChatModel；read_skill/submit mock RestTemplate。
- **端到端**：Claude Desktop 配 SSE 端点 `http://<host>:8087/sse`（含 token），工具列表显示 4 类（6 个 @Tool 方法），逐个调用返回正确结果；`web_fetch` 返回易读 markdown；技能读写正确读写平台技能库。

## 11. 风险

1. **starter 配置/端点细节偏差**（协议模式、端点路径与设计预期不符）→ 已实测验证：`type:SYNC` 触发 SSE 模式（非 streamable-http），端点为 `/sse` + `/mcp/message?sessionId=xxx`。
2. **SSRF**（web_fetch 抓任意 URL）→ 禁内网 IP、超时、响应长度上限；单测覆盖。
3. **表达式注入**（calculator）→ exp4j 白名单运算符 + 长度限制。
4. **外部依赖缺失**（搜索 key / 多模态模型未配）→ 启动校验 + 友好降级（工具不可用时返回明确错误而非崩溃）。
5. **技能读写权限** → 服务账号需有 skill 服务的写权限（`X-User-Id` 透传 `mcp.service-user-id`）。
6. **MCP token 泄露** → token 配 Nacos（不进 git）；后续支持 per-client token。

## 12. Self-Review 结论

- **占位符**：无 TBD/TODO；端点确切路径已标注「实施时验证」。
- **一致性**：§1 范围、§3 架构、§4 工具、§9 步骤全文对齐；工具集恒为 4 类（calculator / web_search+web_fetch / image_analysis / 技能读写）。
- **范围**：单一可交付单元（新 MCP Server 微服务 + 4 类工具）；代码运行工具、管理前端、MCP Client 显式划出本轮不做。
- **关键风险**：starter 行为（§11.1）由步骤 3 最小验证覆盖；SSRF（§11.2）由单测覆盖。
