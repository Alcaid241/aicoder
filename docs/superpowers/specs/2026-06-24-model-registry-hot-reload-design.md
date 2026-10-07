# 模型注册表热重载设计

> **动机**：`DynamicModelRegistry`（现基类 `AbstractDynamicModelRegistry` 在 aicoder-core）是 `@PostConstruct` 启动时一次性把模型读进内存。admin 新增/启停模型后，运行中的 chat/rag/workflow 不感知 → 必须**重启对应服务**才生效（`glm-4.7-flash` 启用后报"不支持的模型"即此因）。本设计给注册表加"热重载"：admin 改完模型配置自动通知三服务重载内存表，实现**启用即生效，免重启**。

## 1. 范围（已与用户确认）

- **触发**：仅 `ModelConfig` 写操作（create / update[含 enabled 启停] / delete）触发重载。`ModelProvider` 变更本轮不触发（YAGNI）。
- **失败策略**：fire-and-forget——admin 保存后异步通知三服务重载；任一服务挂了/重载失败只记日志，不影响 admin 保存，下次重启自然补偿。
- **不做**：provider 变更触发；前端"已生效"反馈；reload 端点鉴权（内部端点，信任内网）；定时轮询。

## 2. 现状（勘察事实）

| 项 | 事实 |
|---|---|
| 注册表 | `AbstractDynamicModelRegistry`（aicoder-core）`@PostConstruct init()` 一次性构建 `chatModels`（HashMap）；chat/rag/workflow 各继承 |
| admin 写端点 | `ModelConfigController @RequestMapping("/api/admin/model/config")`：POST /、PUT /{id}（update，`CreateModelConfigRequest` 含 `enabled` → 覆盖启停）、DELETE /{id} |
| admin 跨服务 | 有 `@LoadBalanced` RestTemplate（`RestTemplateConfig`），但 pom **缺 `spring-cloud-starter-loadbalancer`**（老坑，需补；否则服务名不解析） |
| 跨服务模式 | 项目既有 `@LoadBalanced RestTemplate` → `http://aicoder-<svc>/...`（chat 的 submit_skill_draft 已用） |
| 已有内部端点惯例 | 系统设计曾提 `/internal/...`（绕 Gateway、内部直连）；chat/rag/workflow 自身无鉴权 filter（鉴权在 Gateway） |

## 3. 架构与数据流

```
admin ModelConfigController 写（create / update[含启停] / delete）
   ↓ 持久化到 DB 后
RegistryReloadNotifier.reloadAll()  （@Async，fire-and-forget）
   ↓ @LoadBalanced RestTemplate（Nacos LB 直连，绕过 Gateway）
POST http://aicoder-chat/internal/registry/reload
POST http://aicoder-rag/internal/registry/reload
POST http://aicoder-workflow/internal/registry/reload
   ↓ 各服务 DynamicModelRegistry.reload()
重读 DB → 构建局部新 map → chatModels = newMap（volatile 原子换）→ 重跑探针 → registerExtra
→ 新启用模型即时可用，无需重启
```

## 4. 组件

### 4.1 `aicoder-core`（基类 + 共享 controller）

| 文件 | 改动 | 职责 |
|---|---|---|
| `registry/AbstractDynamicModelRegistry.java` | 改动 | 把 `init()` 构建逻辑抽成 `synchronized reload()`：构建局部新 `HashMap` → 全部建好+探针+`registerExtra` 后 `this.chatModels = newMap`（`chatModels` 改 `volatile`，原子替换，并发读安全）。`init()` 改为只调 `reload()` |
| `web/RegistryReloadController.java` | 新增 | `@RestController @RequestMapping("/internal/registry")`，注入 `AbstractDynamicModelRegistry`，`POST /reload` → `registry.reload()`，返回 200。**一份 controller 服务三 app** |

### 4.2 `chat` / `rag` / `workflow`

| 改动 | 说明 |
|---|---|
| 各 app 类加 `@ComponentScan(basePackages = {"com.ai.coder.<module>", "com.ai.coder.core"})` | 让 core 的 `RegistryReloadController` 被扫描（无需每模块写重复 controller） |

### 4.3 `aicoder-admin`（触发方）

| 文件 | 改动 | 职责 |
|---|---|---|
| `pom.xml` | 加 `spring-cloud-starter-loadbalancer` | 让 @LoadBalanced RestTemplate 解析服务名（老坑） |
| `AicoderAdminApplication.java` | 加 `@EnableAsync` | 支持异步 fire-and-forget |
| `service/RegistryReloadNotifier.java` | 新增 | 注入 @LoadBalanced RestTemplate；`@Async reloadAll()` 对 chat/rag/workflow 各发 `POST http://aicoder-<svc>/internal/registry/reload`，**逐服务 try/catch + log.warn**（不传播异常）。短超时（连接 2s/读 3s）避免 hung 服务长时间占用 |
| `controller/ModelConfigController.java` | 改动 | create/update/delete 保存成功后调 `notifier.reloadAll()`（异步，失败不影响保存结果） |

## 5. 关键决策

1. **`/internal/registry/reload` 不在 `/api/**`** → Gateway 不路由 → admin 经 Nacos LB 直连服务（无需 JWT，内部端点；服务自身无鉴权 filter）。
2. **reload 重跑探针**：复用构建逻辑，新模型顺带被探针校验可达性（与启动一致）。
3. **volatile 原子换 map**：reload 期间 `getChatModel` 读到的是旧表或新表（引用原子替换），不会半破；`reload()` 加 `synchronized` 防并发 reload 互相覆盖。
4. **@Async fire-and-forget**：admin 保存秒回；某服务挂/重载失败只日志（下次重启自然补偿）。短超时防 hung。
5. **触发仅 ModelConfig 写**：provider 变更本轮不触发（YAGNI，以后要再加一个调用点即可）。
6. **一份 controller 在 core**：靠 `@ComponentScan("com.ai.coder.core")` 复用，三 app 无重复代码。

## 6. 错误处理

- **服务不可达/hung**：notifier 每服务独立 try/catch，`log.warn("重载 aicoder-<svc> 失败：{}", msg)`，继续下一个，不抛错。admin 保存照常返回成功。
- **reload 内部异常**（DB 读失败等）：`reload()` 内部构建用 try/catch 包裹单模型构建（沿用现状，单模型失败不阻断其余）；整体 reload 不应抛（若 DB 全挂则日志记录、保持旧表）。
- **重复/并发 reload**：`synchronized` 串行化，幂等（重读 DB 重建，结果一致）。

## 7. 测试

- **core `AbstractDynamicModelRegistryTest`**：新增 `reload` 用例——mock repo 返回新模型集 → 调 `reload()` → 新模型可 `getChatModel` 解析、旧 map 引用被替换、探针对新 Ollama 模型执行。
- **admin `RegistryReloadNotifierTest`**：mock RestTemplate → 验证对 chat/rag/workflow 各发一次 `POST /internal/registry/reload`；mock 某服务抛异常 → 不传播、只日志、其余继续。
- **端到端**：admin 启用一个新模型（如 glm-4.7-flash）→ **不重启 chat** → chat 立即 `getChatModel` 成功（不再抛"不支持的模型"）+ 探针日志。

## 8. 范围边界

- 不触发 provider 变更重载。
- 不做前端"已生效"反馈（admin 保存返回即视为成功，重载异步）。
- 不给 reload 端点加鉴权（内部端点，信任内网；外部访问经 Gateway 时 `/internal/**` 无路由）。
- 不做定时轮询/版本号 diff（admin 主动触发，无需轮询）。

## 9. 风险

1. **admin 缺 loadbalancer starter** → @LoadBalanced 不解析服务名。缓解：本设计显式补该依赖（§4.3）。
2. **@ComponentScan 扫 core 带入意外 bean**：core 当前无其它 @Component（仅新增的 controller），扫描安全；后续若 core 加 bean 需留意。
3. **内部端点暴露**：`/internal/registry/reload` 在服务上无鉴权，任何能直连服务端口者可调。生产环境应限制服务端口仅内网可达（部署约束，非本轮代码问题）。
4. **reload 期间瞬时并发**：volatile 引用替换 + synchronized 保证一致性；getChatModel 拿到旧表最多延迟一个请求到新表，无数据损坏。

## 10. Self-Review 结论

- **占位符**：无 TBD/TODO。
- **一致性**：§3 数据流、§4 组件、§5 决策、§7 测试全文对齐；方法名（`reload()`、`reloadAll()`、`/internal/registry/reload`、`RegistryReloadNotifier`）一致。
- **范围**：单一可交付单元（热重载闭环），provider 触发/前端反馈/鉴权/轮询显式划出。
- **关键风险**：admin loadbalancer starter（§4.3 已覆盖）、@ComponentScan（§9.2 说明）——均可验证覆盖。
