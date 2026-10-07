# 模型管理与动态模型加载 — 设计文档

日期：2026-06-05

## 目标

新增模型管理模块（厂商配置 + 模型配置 + 向量库配置），并重构 chat/rag/workflow 三个模块，使其从数据库动态加载 ChatModel 和 EmbeddingModel，替代 YAML 硬编码配置。

## 决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| 重构范围 | 完整重构 | 新增模型无需改代码重启 |
| 配置管理归属 | aicoder-admin 模块 | 与现有向量库配置放一起，admin 已有管理经验 |
| Embedding 动态化 | Chat + Embedding 全动态化 | 完全可配置 |
| 初始数据 | 自动初始化 Ollama + DeepSeek | 开箱即用 |

---

## 一、数据模型

### 1.1 新增表 `ai_model_provider`（厂商配置）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| name | VARCHAR(50) NOT NULL | 厂商名称，如"Ollama"、"DeepSeek" |
| logo | VARCHAR(200) | 厂商标标 URL 或图标名 |
| code | VARCHAR(30) NOT NULL UNIQUE | 厂商编码，如 OLLAMA、DEEPSEEK、OPENAI |
| base_url | VARCHAR(500) NOT NULL | API 基础地址 |
| api_key | VARCHAR(500) | API Key（可空，Ollama 不需要） |
| description | VARCHAR(500) | 描述 |
| enabled | TINYINT NOT NULL DEFAULT 1 | 启用状态 1=启用 0=禁用 |
| sort | INT NOT NULL DEFAULT 0 | 排序 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 1.2 新增表 `ai_model_config`（模型配置）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| provider_id | BIGINT NOT NULL | 关联厂商 ID |
| display_name | VARCHAR(100) NOT NULL | 模型显示名称，如"Gemma 3 4B" |
| model_code | VARCHAR(100) NOT NULL | 模型编码，如"gemma3:4b"、"deepseek-v4-flash" |
| model_type | VARCHAR(20) NOT NULL | 模型类型：CHAT 或 EMBEDDING |
| enabled | TINYINT NOT NULL DEFAULT 1 | 启用状态 |
| sort | INT NOT NULL DEFAULT 0 | 排序 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 1.3 复用表 `ai_vector_db_config`

现有表结构不变，向量库配置管理 API 保持不变。

---

## 二、后端 — aicoder-admin 模块

### 2.1 新增实体

- `ModelProvider` — 映射 `ai_model_provider`
- `ModelConfig` — 映射 `ai_model_config`

### 2.2 新增 Repository

- `ModelProviderRepository` — findByCode, existsByCode
- `ModelConfigRepository` — findByProviderId, findByModelType, findByEnabled

### 2.3 新增 DTO

- `ProviderDTO` — id, name, logo, code, baseUrl, apiKey, description, enabled, sort
- `CreateProviderRequest` — name, logo, code, baseUrl, apiKey, description, enabled, sort
- `ModelConfigDTO` — id, providerId, providerName, displayName, modelCode, modelType, enabled, sort
- `CreateModelConfigRequest` — providerId, displayName, modelCode, modelType, enabled, sort

### 2.4 新增 Service

- `ModelProviderService` — listProviders, createProvider, updateProvider, deleteProvider
- `ModelConfigService` — listModels, createModel, updateModel, deleteModel, getModelsByType(CHAT/EMBEDDING)

### 2.5 新增 Controller

- `ModelProviderController` — `/api/admin/model/provider`
  - GET /list — 厂商列表
  - GET /{id} — 厂商详情
  - POST — 新增厂商
  - PUT /{id} — 修改厂商
  - DELETE /{id} — 删除厂商（级联检查是否有关联模型）

- `ModelConfigController` — `/api/admin/model/config`
  - GET /list?modelType=CHAT — 模型列表（可按类型筛选）
  - GET /{id} — 模型详情
  - POST — 新增模型
  - PUT /{id} — 修改模型
  - DELETE /{id} — 删除模型

### 2.6 初始数据 — DataInitializer 追加

在现有 admin DataInitializer 中追加逻辑：

**厂商初始化（若 OLLAMA 厂商不存在则创建）：**

| code | name | base_url | api_key |
|------|------|----------|---------|
| OLLAMA | Ollama | http://localhost:11434 | null |
| DEEPSEEK | DeepSeek | https://api.deepseek.com | ${DEEPSEEK_API_KEY} |

**模型初始化：**

| display_name | model_code | model_type | provider |
|-------------|-----------|------------|----------|
| Gemma 3 4B | gemma3:4b | CHAT | Ollama |
| DeepSeek Coder | deepseek-coder | CHAT | Ollama |
| DeepSeek V4 Flash | deepseek-v4-flash | CHAT | DeepSeek |
| Nomic Embed Text | nomic-embed-text | EMBEDDING | Ollama |

---

## 三、后端 — chat/rag/workflow 模块重构

### 3.1 核心组件：DynamicModelRegistry

每个需要 AI 模型的模块（chat、rag、workflow）新增一个 `DynamicModelRegistry`，替代原有的 `ChatModelConfig` / `WorkflowModelConfig`。

**职责：**
1. 应用启动时，从数据库读取所有启用的厂商和模型
2. 根据厂商 code 动态创建对应的 Spring AI 客户端
3. 根据模型配置创建 ChatModel 或 EmbeddingModel 实例
4. 提供 `getChatModel(modelCode)` 和 `getEmbeddingModel()` 方法
5. 提供 `getAvailableChatModels()` 返回可用模型列表

**厂商 → Spring AI 客户端映射：**

| provider.code | 创建的客户端 |
|--------------|-------------|
| OLLAMA | OllamaApi → OllamaChatModel / OllamaEmbeddingModel |
| DEEPSEEK | DeepSeekApi → DeepSeekChatModel |

**关键实现：**

```java
@Component
public class DynamicModelRegistry {
    private final Map<String, ChatModel> chatModels = new HashMap<>();
    private EmbeddingModel embeddingModel;
    private final ProviderRepository providerRepository;
    private final ModelConfigRepository modelConfigRepository;

    @PostConstruct
    public void init() {
        // 1. 读取所有启用厂商
        // 2. 为每个厂商创建 API 客户端
        // 3. 读取所有启用模型
        // 4. 为每个 CHAT 模型创建 ChatModel
        // 5. 为 EMBEDDING 模型创建 EmbeddingModel
    }

    public ChatModel getChatModel(String modelCode) { ... }
    public EmbeddingModel getEmbeddingModel() { ... }
    public List<ModelInfo> getAvailableChatModels() { ... }
}
```

**注意：** 三个模块各自需要自己的 `ModelProvider` 和 `ModelConfig` 实体类（只读），因为这些模块不依赖 admin 模块，它们通过共享 MySQL 数据库直接读取配置表。

### 3.2 各模块具体改造

**aicoder-chat：**
- 删除 `ChatModelConfig`（原硬编码的 modelMap）
- 删除 `ModelService`（原硬编码模型列表）
- 新增 `DynamicModelRegistry`
- `ChatService` 改为注入 DynamicModelRegistry
- `AgentService` 改为注入 DynamicModelRegistry
- `ModelController.models()` 改为从 DynamicModelRegistry 获取可用模型
- 修改 application.yml — 保留 base-url/api-key，移除 chat.options

**aicoder-rag：**
- 删除 `ChatModelConfig`（原 defaultChatModel + chatModelMap）
- 新增 `DynamicModelRegistry`
- `RagChatService` 改为注入 DynamicModelRegistry
- `SqlGenerationService` 改为注入 DynamicModelRegistry
- `DynamicVectorStoreConfig` 改为从 DynamicModelRegistry 获取 EmbeddingModel
- 移除 YAML 中的 `spring.ai.ollama` 和 `spring.ai.deepseek` 配置

**aicoder-workflow：**
- 删除 `WorkflowModelConfig`（原硬编码的 modelMap）
- 新增 `DynamicModelRegistry`
- `WorkflowExecutor` 改为注入 DynamicModelRegistry
- `DynamicVectorStoreConfig` 改为从 DynamicModelRegistry 获取 EmbeddingModel
- 移除 YAML 中的 `spring.ai.ollama` 和 `spring.ai.deepseek` 配置

### 3.3 Spring AI 自动配置处理

采用方案：**保留最小 YAML 配置 + 手动创建所有 Model Bean**。

每个模块的 YAML 中仅保留 Ollama 的 `base-url` 和 DeepSeek 的 `api-key`（Spring AI 自动配置需要这些才能创建 OllamaApi / DeepSeekApi Bean），移除所有 `chat.options` 和 `embedding.options` 配置（阻止自动创建 ChatModel/EmbeddingModel Bean）。

DynamicModelRegistry 注入 Spring AI 自动创建的 OllamaApi / DeepSeekApi Bean，然后手动创建所有 ChatModel 和 EmbeddingModel 实例。

YAML 变更示例：
```yaml
# 保留（DynamicModelRegistry 需要这些 API 客户端）
spring.ai.ollama.base-url: http://localhost:11434
spring.ai.deepseek.api-key: sk-xxx

# 移除（不再需要，由 DynamicModelRegistry 从数据库读取）
# spring.ai.ollama.chat.options.model: gemma3:4b
# spring.ai.ollama.chat.options.temperature: 0.7
# spring.ai.ollama.embedding.options.model: nomic-embed-text
# spring.ai.deepseek.chat.options.model: deepseek-chat
# spring.ai.deepseek.chat.options.temperature: 0.7
```

---

## 四、前端

### 4.1 新增页面

**厂商配置页 `ProviderConfigView.vue`：**
- 表格展示：厂商名称、商标、编码、Base URL、状态、操作
- 新增/编辑弹窗：厂商名称（默认空）、商标（默认空）、厂商编码（默认空）、Base URL（默认 `http://localhost:11434`）、API Key（默认空）、描述、启用状态（默认启用）
- 删除：检查是否有关联模型

**模型配置页 `ModelConfigView.vue`：**
- 表格展示：模型名称、模型编码、所属厂商、模型类型（CHAT/EMBEDDING 标签）、状态、操作
- 新增/编辑弹窗：关联厂商（下拉选择）、模型显示名称、模型编码、模型类型（下拉 CHAT/EMBEDDING）、启用状态
- 删除：直接删除

**向量库配置页迁移 `VectorDbConfigView.vue`：**
- 从 `views/admin/` 迁移到 `views/model/` 下
- 逻辑不变，仅调整路由和菜单位置

### 4.2 API 模块

新增 `src/api/model.ts`：
- `modelApi.providerList()` / `.providerCreate()` / `.providerUpdate()` / `.providerDelete()`
- `modelApi.configList()` / `.configCreate()` / `.configUpdate()` / `.configDelete()`

### 4.3 路由

新增路由：
- `/model/provider` → ProviderConfigView
- `/model/config` → ModelConfigView
- `/model/vectordb` → VectorDbConfigView（从 `/vectordb` 迁移）

### 4.4 菜单数据

DataInitializer 中新增：
- 一级目录"模型管理"（path=/model, type=1, icon=Model, sort=4，在工作流之后）
- 二级菜单：厂商配置、模型配置、向量库配置

同时移除原来的一级菜单"向量库配置"（因为已移到模型管理下）

---

## 五、文件变更清单

### admin 模块新增
- entity/ModelProvider.java
- entity/ModelConfig.java
- repository/ModelProviderRepository.java
- repository/ModelConfigRepository.java
- dto/ProviderDTO.java, CreateProviderRequest.java
- dto/ModelConfigDTO.java, CreateModelConfigRequest.java
- service/ModelProviderService.java
- service/ModelConfigService.java
- controller/ModelProviderController.java
- controller/ModelConfigController.java

### admin 模块修改
- init/DataInitializer.java — 追加厂商和模型初始数据

### chat 模块新增
- entity/ModelProvider.java（只读）
- entity/ModelConfig.java（只读）
- repository/ModelProviderRepository.java
- repository/ModelConfigRepository.java
- registry/DynamicModelRegistry.java

### chat 模块修改
- 删除 config/ChatModelConfig.java
- 删除 service/ModelService.java
- 修改 service/ChatService.java — 注入 DynamicModelRegistry
- 修改 service/AgentService.java — 注入 DynamicModelRegistry
- 修改 controller/ModelController.java — 从 DynamicModelRegistry 获取模型列表
- 修改 application.yml — 保留 base-url/api-key，移除 chat.options 和 embedding.options

### rag 模块新增
- entity/ModelProvider.java（只读）
- entity/ModelConfig.java（只读）
- repository/ModelProviderRepository.java
- repository/ModelConfigRepository.java
- registry/DynamicModelRegistry.java

### rag 模块修改
- 删除 config/ChatModelConfig.java
- 修改 service/RagChatService.java — 注入 DynamicModelRegistry
- 修改 service/SqlGenerationService.java — 注入 DynamicModelRegistry
- 修改 config/DynamicVectorStoreConfig.java — 从 DynamicModelRegistry 获取 EmbeddingModel
- 修改 application.yml — 保留 base-url/api-key，移除 chat.options 和 embedding.options

### workflow 模块新增
- model/entity/ModelProvider.java（只读）
- model/entity/ModelConfig.java（只读）
- repository/ModelProviderRepository.java
- repository/ModelConfigRepository.java
- registry/DynamicModelRegistry.java

### workflow 模块修改
- 删除 config/WorkflowModelConfig.java
- 修改 executor/WorkflowExecutor.java — 注入 DynamicModelRegistry
- 修改 config/DynamicVectorStoreConfig.java — 从 DynamicModelRegistry 获取 EmbeddingModel
- 修改 application.yml — 保留 base-url/api-key，移除 chat.options 和 embedding.options

### 前端新增
- src/api/model.ts
- src/views/model/ProviderConfigView.vue
- src/views/model/ModelConfigView.vue

### 前端修改
- src/views/admin/VectorDbConfigView.vue → 迁移到 src/views/model/VectorDbConfigView.vue
- src/router/index.ts — 新增模型管理路由，移除旧向量库路由
- src/types/index.ts — 新增 Provider/ModelConfig 相关类型
- src/api/vectorDb.ts — 无变化

### system 模块修改
- init/DataInitializer.java — 菜单结构调整（新增模型管理目录，移除原向量库一级菜单）

---

## 六、初始菜单最终结构

| sort | 菜单名 | path | type | parentId |
|------|--------|------|------|----------|
| 1 | 首页 | /home | 2 | null |
| 2 | 对话 | /chat | 2 | null |
| 3 | 知识库管理 | /knowledge-mgmt | 1 | null |
| 1 | 知识库 | /knowledge | 2 | 知识库管理 |
| 2 | RAG 对话 | /rag | 2 | 知识库管理 |
| 3 | NL2SQL | /sql | 2 | 知识库管理 |
| 4 | 工作流 | /workflow | 2 | null |
| 5 | 模型管理 | /model | 1 | null |
| 1 | 厂商配置 | /model/provider | 2 | 模型管理 |
| 2 | 模型配置 | /model/config | 2 | 模型管理 |
| 3 | 向量库配置 | /model/vectordb | 2 | 模型管理 |
| 6 | 个人信息 | /profile | 2 | null |
| 7 | 系统管理 | /system | 1 | null |
| 1 | 用户管理 | /system/user | 2 | 系统管理 |
| 2 | 角色管理 | /system/role | 2 | 系统管理 |
| 3 | 权限管理 | /system/permission | 2 | 系统管理 |
| 4 | 菜单管理 | /system/menu | 2 | 系统管理 |
