# Agent Skill 管理面 Phase 1 验证修复设计

> **关联：** Phase 1 实现计划 [2026-06-13-agent-skill-phase1-management-plane.md](../plans/2026-06-13-agent-skill-phase1-management-plane.md)；Phase 1 系统设计 [2026-06-13-agent-skill-system-design.md](2026-06-13-agent-skill-system-design.md)。

## 背景

Phase 1 管理面（`aicoder-skill` :8086）已实现并通过端到端验证（Task 10，2026-06-14）。验证暴露两个需在 Phase 2 前处理的问题：

1. **`skill.directory: ./skills` 是相对 JVM 工作目录的路径。** 同一 `greeting-skill` 种子在 `mvn spring-boot:run -pl aicoder-skill`（cwd=模块目录）时物化到 `aicoder-skill/skills/`，而从仓库根 `java -jar` 启动时物化到仓库根 `skills/`。Phase 2 的 chat 运行面 `SkillRegistry` 必须读取 skill-service 写入的同一目录，否则会读到错误（空）的技能目录。
2. **错误码语义不准。** `GlobalExceptionHandler` 把所有 `RuntimeException` 映射成 `400 BAD_REQUEST`：`getById` 未命中（应为 404）、非法状态迁移（应为 409）、重名（应为 409）全报 400，且 NPE 等意外异常也会被误报成 400。

## 目标

- **Fix 1**：把 `skill.directory` 提升为**跨服务共享真相源**——抽到 Nacos 配置（data-id `aicoder-shared.yml`），未来 chat 直接读同一值；同时启动时解析为绝对路径并高亮日志，让 cwd 混淆一眼可见。
- **Fix 2**：用领域异常 + 精确 HTTP 映射重建错误模型：404 / 409 / 500 各归其位，错误体格式统一。

## 非目标（本次不做）

- chat 运行面接入（`SkillRegistry`/`SkillPromptAugmentAdvisor`）——Phase 2。
- Controller 返回类型从 `Skill` 实体改为 `SkillDTO`（验证中列为 context，非 bug）。
- 已 ACTIVE 技能在 `skill.directory` 变更后的批量重物化/迁移。

## 现状关键事实

- **项目当前零 Nacos-config 基础设施**：无任何 pom 引 `spring-cloud-starter-alibaba-nacos-config`，无任何 `spring.cloud.nacos.config` 配置块，无任何 Nacos 配置监听器。所有 `bootstrap.yml` 仅配置 `discovery`。模型/向量库配置是 DB-backed（`ModelConfigService`/`VectorDbConfigService`），CLAUDE.md 中"DynamicModelRegistry 监听 Nacos config"的说法不准确。
- **无 `spring-cloud-starter-bootstrap`**：现有 `bootstrap.yml` 的 discovery 配置很可能靠 Nacos 默认值（discovery 默认连 `localhost:8848` 并自动注册）生效，而非 bootstrap context。因此 Nacos **config** 接入应使用 Spring Cloud 2025 的 `spring.config.import` 机制（写在 `application.yml`），不要依赖 bootstrap context。
- 父 pom 已导入 `spring-cloud-alibaba-dependencies 2025.0.0.0` 与 `spring-cloud-dependencies 2025.0.0` BOM，`nacos-config` 依赖无需显式版本。

---

## Fix 1 设计：`skill.directory` → Nacos 共享配置

### 依赖

`aicoder-skill/pom.xml` 增加（版本由父 BOM 管理）：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

### 配置源（Nacos data-id）

在运行中的 Nacos（`localhost:8848`）创建 data-id `aicoder-shared.yml`（group `DEFAULT_GROUP`），内容：

```yaml
skill:
  directory: /Users/haijingxu/workspace/claudeCode/aicoder/skills
```

> 该 data-id 设计为**多服务共享**：Phase 2 的 chat 引入同一 `spring.config.import` 即读到同一目录。本地开发用仓库根下的绝对路径；生产/部署用共享挂载卷的绝对路径。

### 配置加载机制（Spring Cloud 2025）

在 `aicoder-skill/src/main/resources/application.yml` 增加 config-import（**不**依赖 bootstrap context）：

```yaml
spring:
  config:
    import:
      - optional:nacos:aicoder-shared.yml
  cloud:
    nacos:
      server-addr: localhost:8848   # config 与 discovery 共用
```

`optional:` 前缀确保 Nacos 不可用时服务仍可启动（退回本地默认）。

### 本地兜底 + env 覆盖

`application.yml` 保留 `skill` 块作兜底，支持 `${SKILL_DIRECTORY:./skills}` 环境变量覆盖：

```yaml
skill:
  directory: ${SKILL_DIRECTORY:./skills}
```

配置优先级：Nacos `aicoder-shared.yml` > 本地 `application.yml`（Spring Cloud config import 默认高于本地文件）。

### 解析与可见性

`SkillProperties` 在 `@PostConstruct` 把 `directory` 解析为绝对路径并 `WARN` 级别打印，使 cwd 混淆在启动日志即可发现：

```
[skill] directory resolved to: /Users/haijingxu/.../skills (source: nacos|env|default)
```

实现：`directory = new File(directory).getAbsoluteFile().getPath()`，并标注来源（Nacos 命中则 source=nacos）。

### 动态刷新

`SkillProperties` 加 `@RefreshScope`。Nacos 修改 `skill.directory` 后，**新审批激活的技能**物化到新路径。已知限制：刷新**不触发**已 ACTIVE 技能的重物化/迁移——已物化文件停留在旧路径，旧路径下的文件不会被清理。该限制在本设计明确记录，留待后续按需处理。

---

## Fix 2 设计：错误码语义清理

### 异常类型

在 `com.ai.coder.skill.exception` 包下新增：

| 异常 | 抛出点 | HTTP |
|------|--------|------|
| `SkillNotFoundException extends RuntimeException` | `SkillService.getById` 未命中 | 404 |
| `DuplicateSkillException extends RuntimeException` | `SkillService.create` 重名 | 409 |
| `IllegalSkillStateException extends RuntimeException` | `SkillLifecycleService.assertTransition` 非法迁移 | 409 |

### 映射

`GlobalExceptionHandler` 重构为精确映射：

```java
@ExceptionHandler(SkillNotFoundException.class)   → 404
@ExceptionHandler({DuplicateSkillException.class, IllegalSkillStateException.class})  → 409
@ExceptionHandler(Exception.class)                → 500   // 兜底，含 NPE 等意外异常
```

移除原 `RuntimeException → 400` 的宽泛映射。不再有"未知业务异常被报成 400"。

### 错误体格式

统一为（与 gateway 的 401 体格式一致）：

```json
{ "code": 404, "message": "技能不存在：999" }
```

### 调用点改动

- `SkillService.getById`：`throw new RuntimeException("技能不存在：" + id)` → `throw new SkillNotFoundException("技能不存在：" + id)`。
- `SkillService.create`：`throw new IllegalStateException("技能名已存在：" + ...)` → `throw new DuplicateSkillException(...)`。
- `SkillLifecycleService.assertTransition`：`throw new IllegalStateException(...)` → `throw new IllegalSkillStateException(...)`。

---

## 测试（TDD）

### 新增 / 改造测试

- **`SkillServiceTest`**（改造）：
  - `getById` 未命中 → 抛 `SkillNotFoundException`（断言异常类型）。
  - `create` 重名 → 抛 `DuplicateSkillException`。
  - 非法迁移（如 `approve` 一个 ARCHIVED）→ 抛 `IllegalSkillStateException`。
  - 现有用例断言 `IllegalStateException.class` 的改为 `IllegalSkillStateException.class`。
- **`GlobalExceptionHandlerTest`**（新增，`@WebMvcTest` + MockMvc 或直接单测 handler）：
  - 三种异常分别映射 404 / 409 / 500，响应体含正确 `code` 与 `message`。
- **`SkillPropertiesTest`**（新增）：
  - 相对路径 `./skills` 解析为绝对路径且非空。
  - env `SKILL_DIRECTORY` 注入值被采用。
- **`SkillRegistrySyncServiceTest` / `SkillServiceTest`**（现有）：用 `@TempDir` 注入绝对路径，不受 cwd 解析影响——确认仍全绿。

### 端到端回归（可选，需中间件）

启动 skill + gateway，复跑 Task 10 的非法迁移/重名/未命中用例，确认响应码变为 409/409/404。

---

## 范围边界与风险

- **Nacos-config 是本项目首个 config 接入点**：实现时需验证 `spring.config.import: optional:nacos:aicoder-shared.yml` 确实加载（启动日志确认 source=nacos）。这是本次最高风险接线点，计划里作为独立验证步骤。
- **`aicoder-shared.yml` 需在 Nacos 手动创建**：实现步骤给出 Nacos 控制台 / OpenAPI 做法。本地开发若不建该 data-id，`optional:` 前缀保证退回本地默认，服务仍可启动（只是不共享）。
- 不动 chat、前端、网关路由。
- Controller 返回 `Skill` 实体的现状保留。

## Self-Review 结论

- **占位符**：无 TBD/TODO；所有代码片段完整。
- **一致性**：异常类型、抛出点、HTTP 映射、测试断言全文对齐；错误体格式与 gateway 404/401 体一致。
- **范围**：聚焦两个验证发现，单一实现计划可覆盖；Phase 2 接入路径（chat 读同一 data-id）已预留。
- **关键风险**：Nacos config import 首次接入——计划中以独立验证步骤覆盖；`optional:` 兜底保证不影响本地启动。
