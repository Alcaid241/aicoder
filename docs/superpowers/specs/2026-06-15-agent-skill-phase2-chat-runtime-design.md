# Agent Skill 体系 — Phase 2：chat 运行面读侧接入设计

> **关联：** 系统设计 [2026-06-13-agent-skill-system-design.md](2026-06-13-agent-skill-system-design.md)（§8 运行面改动）、Phase 1 管理面计划 [2026-06-13-agent-skill-phase1-management-plane.md](../plans/2026-06-13-agent-skill-phase1-management-plane.md)、Phase 1 验证修复 [2026-06-14-skill-config-nacos-and-error-codes-design.md](2026-06-14-skill-config-nacos-and-error-codes-design.md)。

## 1. 背景

Phase 1（管理面 `aicoder-skill` :8086）已完成：技能 CRUD、状态机、审批、把 ACTIVE 技能物化为 `SKILL.md` 写入共享目录（经 Nacos `aicoder-shared.yml` 的 `skill.directory` 统一为绝对路径），并升级 Nacos 到 3.x 让 config gRPC 通路可用。

本阶段（Phase 2）让 `aicoder-chat` (:8082) **消费**这些技能：把 ACTIVE 技能目录注入对话系统提示，模型匹配时按需 `read_skill(name)` 加载完整 SKILL.md 套用——即设计的「渐进式披露」读侧。**范围只做读侧**；`submit_skill_draft` 写回与自进化闭环划为 Phase 3。

## 2. 调研结论（决定本阶段技术选型）

| 事实 | 来源/校验 |
|---|---|
| chat 当前是**手搓**链路：直接 `ChatModel.call()/stream()`，无 `ChatClient`、无 advisor 链、无 Spring AI 工具调用；`AgentService` 是 JSON 解析的 ReAct 循环 + 硬编码 `switch` 分发 3 个工具 | chat 模块代码勘察 |
| `spring-ai-alibaba-graph-core@1.1.2.2`（workflow 已用）含**注册表 + advisor 层**：`FileSystemSkillRegistry`、`SkillPromptAugmentAdvisor`、`SpringAiSkillAdvisor`、`SkillScanner`、`SkillMetadata` | jar 反查 + [官方 Skills 文档](https://java2ai.com/docs/frameworks/agent-framework/tutorials/skills) |
| 同 jar **不含** `ReactAgent`/`SkillsAgentHook`/`ReadSkillTool`（agent 包不在 1.1.2.2）；`agent-framework` 仅 BOM 管理 @1.1.2.0（低于设计文档要求的 1.1.2.2+ 成熟度），未拉取、成熟度未验证 | jar 反查 + BOM 反查 |
| `SkillPromptAugmentAdvisor`（`implements BaseAdvisor`）在 `before()` 把技能目录（name+description+path）注入系统提示，但**不注册 read_skill 工具** | 源码 |
| `SkillRegistry` 接口提供 `readSkillContent(name)`、`listAll()`、`reload()` 等 | 源码 |

**结论**：设计的「ReactAgent + SkillsAgentHook」原生全闭环在当前可解析版本（1.1.2.2）不可得（见系统设计 §10.1 风险，现已确认）。可得的 native 能力是 **advisor 注入目录 + FileSystemSkillRegistry 读 SKILL.md**；`read_skill` 工具需自建薄封装。故采用 **A′ 方案**：`ChatClient` + `SkillPromptAugmentAdvisor`（原生）+ 自建 `read_skill` 工具。

## 3. 架构

```
用户消息(/api/chat/send|/stream) → ChatService
  chatModel = DynamicModelRegistry.getChatModel(modelCode)   # 既有，按请求解析
  ChatClient.builder(chatModel)
    .defaultAdvisors(skillPromptAugmentAdvisor)   # native: before() 注入技能目录
    .defaultTools(readSkillTool)                  # 自建: read_skill(name)
    .build()
  chatClient.prompt()
    .system(<既有系统提示>)          # advisor 在此之上追加技能目录
    .messages(<会话历史>)
    .user(<用户消息>)
    .call() / .stream()             # 非流式取 content / 流式 SSE
```

技能目录来源：`FileSystemSkillRegistry` 指向 Nacos `aicoder-shared.yml` 的 `skill.directory`（与 skill 服务同一绝对路径，Phase 1 已验证）。

## 4. 组件清单（`com.ai.coder.chat`）

| 组件 | 新增/改动 | 职责 |
|---|---|---|
| `config/SkillRuntimeConfig` | 新增 | 构建 `FileSystemSkillRegistry`(skill.directory) + `SkillPromptAugmentAdvisor`(registry) 单例 bean；读 chat 自己的 `SkillProperties` |
| `config/SkillProperties` | 新增 | `@ConfigurationProperties("skill")` + `directory` + `getResolvedDirectory()`(绝对解析) + `@PostConstruct` WARN 日志 + `@RefreshScope`（复刻 aicoder-skill 同名类；无 common 模块，接受此重复） |
| `tool/ReadSkillTool` | 新增 | `@Tool(description="...") readSkill(String skillName)` → `registry.readSkillContent(name)`；缺失返回友好提示；非法名防穿越（复用 Phase 1 校验） |
| `service/ChatService` | 改动 | `chat()`/`chatStream()` 改用 `ChatClient`；保留系统提示、会话历史装配与 SSE 事件结构；保留 `DynamicModelRegistry` |
| `application.yml` | 改动 | `spring.config.import: optional:nacos:aicoder-shared.yml` + `spring.cloud.nacos.server-addr` + `skill.directory: ${SKILL_DIRECTORY:./skills}` |
| `pom.xml` | 改动 | 加 `spring-ai-alibaba-graph-core@1.1.2.2`（显式版本，同 workflow）+ `spring-cloud-starter-alibaba-nacos-config` |

## 5. 关键决策

1. **只接 `ChatService`（普通对话），不动 `AgentService`。** 技能匹配的主场景是普通对话；`AgentService` 手搓 ReAct 不走 ChatClient，advisor 接不进去，留待后续（含 3 工具迁移到 ToolCallback 的更大重构）。
2. **`read_skill` 自建。** graph-core 1.1.2.2 无框架版 read_skill；但 `FileSystemSkillRegistry.readSkillContent(name)` 已能取正文，工具只是 ~15 行封装。
3. **目录共享复用 Phase 1 通路。** chat 读同一 `aicoder-shared.yml`，经已验证的 Nacos 3.x + `optional:` 兜底；chat 侧同样做绝对路径解析 + 启动 WARN 日志（复刻 `SkillProperties`，防 cwd 混淆）。
4. **ChatClient 按请求构建。** `DynamicModelRegistry` 按请求解析 chatModel，故 `ChatClient.builder(chatModel)` 每请求新建（轻量）。advisor/registry/tool bean 单例复用。
5. **工具调用前置：read_skill 需模型支持 function-calling。** DeepSeek 支持；Ollama 视模型而定。若当前模型不支持工具调用，**目录注入仍生效**（advisor 与工具能力无关），仅无法触发 read_skill（渐进式披露降级为「只感知目录」）。验收以支持 function-calling 的模型为准。

## 6. 数据流

1. 用户在 chat 提问 → `ChatService` 解析模型、构建 `ChatClient`（挂 advisor + read_skill）。
2. `SkillPromptAugmentAdvisor.before()` 把 ACTIVE 技能目录追加进系统提示。
3. 模型评估：**匹配**某技能 → 调 `read_skill("name")` → `ReadSkillTool` → `registry.readSkillContent(name)` 返回完整 SKILL.md → 模型套用作答。
4. **不匹配**（或目录为空） → 直接作答（Phase 2 不处理「缺失则生成」，那是 Phase 3）。
5. 流式路径经 `chatClient...stream()` 产出 SSE，保持现有前端契约。

## 7. 测试（TDD）

- **`ReadSkillToolTest`**：命中技能→返回 SKILL.md 正文；缺失技能→友好提示；路径穿越名（`../`）→ 拒绝。
- **`SkillRuntimeConfigTest`**：`@TempDir` 注入含一个示例 `{name}/SKILL.md` 的目录，registry 能加载、`SkillPromptAugmentAdvisor` 可构建、`listAll()` 含该技能。
- **端到端（手动，需中间件）**：启动 chat + skill + gateway + Nacos（已 3.x）；用支持 function-calling 的模型，问「你有哪些技能」应列出 `greeting-skill`（Phase 1 种子的 ACTIVE 技能）；追问其用法应触发 `read_skill("greeting-skill")` 加载完整内容。日志确认 `skill.directory` 来自 Nacos（非本地默认）。

## 8. 范围边界（Phase 2 不做）

- `submit_skill_draft` 写回 / 元技能生成 / 质量打分 / 审批队列前端（Phase 3）。
- `AgentService` 技能化；`knowledge_search`/`sql_query`/`http_request` 三工具迁移到 ToolCallback。
- rag/workflow 技能接入（同构，后续）。
- 目录热刷新：`FileSystemSkillRegistry.reload()` 在 agent 调用前触发（可选增强，初版启动扫描即可；新增技能需 chat 重启或后续接 `/internal/skills/reload`）。

## 9. 风险

1. **模型工具调用能力**：见 §5.5。验收须用支持 function-calling 的模型；非支持模型降级为只注入目录。
2. **ChatClient 重构回归**：`ChatService` 改动影响普通对话主路径；需保留现有 SSE 事件结构与系统提示语义，端到端回归普通对话不退化。
3. **SkillProperties 重复**：与 aicoder-skill 同名类重复（无 common 模块）；接受此重复，后续可抽 `aicoder-common`。
4. **Spring AI 版本**：graph-core 1.1.2.2 与 spring-ai 1.1.2（chat 现用）需共存；workflow 已验证此组合可编译运行。

## 10. Self-Review 结论

- **占位符**：无 TBD/TODO。
- **一致性**：组件清单、数据流、测试、决策全文对齐；类名/方法名（`SkillPromptAugmentAdvisor`、`FileSystemSkillRegistry.readSkillContent`、`ReadSkillTool.readSkill`）与 graph-core 1.1.2.2 实际 API 一致。
- **范围**：单一可交付单元（chat 读侧），Phase 3/前端/写回显式划出。
- **关键风险**：模型工具调用能力、ChatClient 重构回归——均可在测试与端到端覆盖。
