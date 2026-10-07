# DynamicModelRegistry 去重 — 抽取 `aicoder-core` 共享模块设计

> **动机**：chat/rag/workflow 三模块各有一份近乎相同的 `DynamicModelRegistry`（161/162/162 行，~90% 重复：init / createChatModel / probeOllama / DeepSeek 客户端 / getChatModel / getChatModelMap），且各自的 `ModelConfig`/`ModelProvider` 实体与 Repository 也 ×3 重复。最近给三份 registry 各加了一遍启动探针，重复维护成本凸显。本设计把共享逻辑抽到新的 `aicoder-core` 模块，**改一次生效三处**，不引入运行时耦合。

## 1. 范围（已与用户确认）

- **目标**：去重 / 可维护性（每次改模型逻辑只维护一份）。
- **手段**：新建 `aicoder-core` 编译期共享模块（jar），含共享实体/Repository + 基类 `AbstractDynamicModelRegistry`。chat/rag/workflow 继承基类、只补各自差异。
- **不做**：运行时中心化模型服务（那会引入网络依赖/SPOF/丢内存缓存，属于另一个目标，本轮明确不做）；动 rag/workflow 的 `KnowledgeBase`/`VectorDbConfig` 等非模型实体。

## 2. 现状（勘察事实）

| 项 | 事实 |
|---|---|
| common 模块 | **无**（父 pom 7 个模块全是独立服务） |
| 三份 DynamicModelRegistry | chat 161 / rag 162 / workflow 162 行；init/createChatModel/probeOllama/deepSeek 客户端/getChatModel/getChatModelMap 全同 |
| 差异面 | rag/workflow 多 `embeddingModel` + `getEmbeddingModel()`；chat 多 `getAvailableChatModels()`（给前端列模型，返回 `ModelInfoDTO`） |
| ModelConfig/ModelProvider + 2 个 Repository | 每模块各一份（结构全同），×3 重复 |

## 3. 架构

```
aicoder-core (新, com.ai.coder.core, 编译期 jar)
  ├── entity/ModelConfig, entity/ModelProvider          ← 三模块合一
  ├── repository/ModelConfigRepository, ModelProviderRepository
  └── registry/AbstractDynamicModelRegistry             ← 全部共享逻辑 + 探针 + embedding 钩子

aicoder-chat   DynamicModelRegistry  extends Abstract  ← +getAvailableChatModels()
aicoder-rag    DynamicModelRegistry  extends Abstract  ← +embeddingModel + registerExtra() + getEmbeddingModel()
aicoder-workflow DynamicModelRegistry extends Abstract ← 同 rag
```

**关键性质**：common 是编译期依赖（jar），每个服务仍在**自己 JVM** 里 `@PostConstruct` 建内存模型——无网络跳、无 SPOF、无延迟，保留现有 per-service 缓存速度。

## 4. 组件

### 4.1 `aicoder-core`

| 文件 | 职责 |
|---|---|
| `pom.xml` | 父=aicoder；依赖 `spring-boot-starter-data-jpa`（实体/Repository）、`spring-ai-*`（基类用 ChatModel/OllamaChatModel/DeepSeekChatModel/Embedding 等）、lombok |
| `entity/ModelConfig` | 迁入（字段同现有） |
| `entity/ModelProvider` | 迁入 |
| `repository/ModelConfigRepository` | `JpaRepository<ModelConfig,Long>` + `findByEnabledOrderBySortAsc(int)` |
| `repository/ModelProviderRepository` | 同上 |
| `registry/AbstractDynamicModelRegistry` | 见 §4.2 |

### 4.2 `AbstractDynamicModelRegistry`（基类 API）

```
abstract class AbstractDynamicModelRegistry {
  protected final ModelConfigRepository modelConfigRepository;   // protected，子类可用
  protected final ModelProviderRepository providerRepository;
  protected final Map<String, ChatModel> chatModels = new HashMap<>();

  @PostConstruct void init() {
    // 遍历 enabled providers+models：CHAT→createChatModel+put+probeOllama(若 OLLAMA)；随后 registerExtra(models, providerMap)
  }
  ChatModel createChatModel(provider, code)        // switch OLLAMA/DEEPSEEK
  void probeOllama(baseUrl, displayName, code)     // 启动探针（最近加的，三模块共用此一份）
  ChatModel getChatModel(code) / Map getChatModelMap()
  protected void registerExtra(models, providerMap) {}  // 钩子，默认空
}
```

基类**不感知 embedding**（chat 不需要）；embedding 由 rag/workflow 子类在 `registerExtra` 钩子里建。

### 4.3 子类（只补差异）

- **chat** `DynamicModelRegistry extends AbstractDynamicModelRegistry`：仅 `getAvailableChatModels()`（读 DB，返回 chat 自己的 `ModelInfoDTO`，用基类 protected 的 repo）。删 chat 的 ModelConfig/ModelProvider/2 repo。
- **rag** / **workflow**：加 `private EmbeddingModel embeddingModel`；`@Override registerExtra()` 在 enabled EMBEDDING 模型里建 `OllamaEmbeddingModel`（首个即用，沿用现状）；`getEmbeddingModel()`（缺失抛 IllegalStateException）。删各自的 4 个实体/repo。

## 5. 实体 / Repository 扫描配置（迁移关键风险点）

common 实体在 `com.ai.coder.core.entity`，各服务 `@SpringBootApplication` 在 `com.ai.coder.<module>`——JPA 默认只扫本包，common 包不扫 → 启动报 "Not a managed type"。

**每个 app 类（chat/rag/workflow）加**：
```java
@EntityScan(basePackages = {"com.ai.coder.<module>", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.<module>", "com.ai.coder.core"})
```
实施前先核各 app 现有注解（部分可能已有 `@EntityScan`），合并而非重复声明。

## 6. 迁移步骤（顺序）

1. 建 `aicoder-core`（pom + 实体 + repo + 基类含探针）；父 pom 加 module。
2. chat/rag/workflow pom 各加 `aicoder-core` 依赖。
3. **chat**：改继承 + 加实体扫描 + 删本地实体/repo → 编译 + 重启验证（注册日志不变、探针照常）。
4. **rag**：同上 + embedding 钩子 → 验证（chat 模型 + embedding 都注册）。
5. **workflow**：同 rag。
6. 全量重启三模块，端到端验证。

每步独立可编译可验证，出问题可定位到具体模块。

## 7. 测试

- **编译**：common + chat + rag + workflow 全绿。
- **重启验证**：各模块 `注册 Chat 模型` / `注册 Embedding 模型` 日志与重构前一致；启动探针照常（复跑假模型 WARN 测试，确认探针逻辑在基类里仍生效）。
- **功能回归**：chat 对话、rag 检索（用 embedding）各跑一次，确认模型解析正常、无 "Not a managed type" 等扫描报错。

## 8. 风险

1. **实体扫描配置遗漏** → 启动报 Not a managed type。缓解：每个 app 必加 `@EntityScan`+`@EnableJpaRepositories` 含 common 包；§7 验证覆盖。
2. **Repository bean 冲突**：common repo 与模块残留 repo 同名/同接口 → 删除模块本地副本即可（不并存）。
3. **rag/workflow 的 KnowledgeBase/VectorDbConfig 等实体不动**：只迁 ModelConfig/ModelProvider，范围严格限定，避免误伤。
4. **行为漂移**：基类化后 init 语义须与现状一致（CHAT 先建、EMBEDDING 经钩子建、探针仅在 OLLAMA）。逐模块编译+重启验证把关。

## 9. Self-Review 结论

- **占位符**：无 TBD/TODO。
- **一致性**：§3 架构、§4 组件、§5 扫描配置、§6 迁移步骤全文对齐；类名/方法名（`AbstractDynamicModelRegistry`、`registerExtra`、`probeOllama`）一致。
- **范围**：单一可交付单元（抽 common 模块 + 迁移三模块），运行时中心化/非模型实体显式划出。
- **关键风险**：实体扫描配置（§5）——可由逐模块重启验证覆盖。
