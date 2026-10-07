# 抽取 aicoder-core 去重 DynamicModelRegistry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 chat/rag/workflow 三模块重复的 `DynamicModelRegistry`（+ ModelConfig/ModelProvider 实体与 Repository）抽到新 `aicoder-core` 共享模块的基类，改一次生效三处，不引入运行时耦合。

**Architecture:** 新建 `aicoder-core`（编译期 jar，含共享实体/Repository + `AbstractDynamicModelRegistry` 基类，承载 init/createChatModel/probeOllama + embedding 钩子）。chat/rag/workflow 各自的 `DynamicModelRegistry` 改为继承基类、只补差异（chat:+getAvailableChatModels；rag/workflow:+embedding 钩子），删除各自本地实体/repo 副本。每个服务仍在自己 JVM 内 @PostConstruct 建内存模型，无网络/SPOF。

**Tech Stack:** Spring Boot 3.5 / Spring AI 1.1.2（OllamaChatModel/DeepSeekChatModel/OllamaEmbeddingModel）/ Maven 多模块 / JPA。

**Build/test 命令：** `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`

**Spec:** [2026-06-23-dynamic-model-registry-common-module-design.md](../specs/2026-06-23-dynamic-model-registry-common-module-design.md)

**勘察事实（已核实）：**
- 无 common 模块；父 pom `<modules>` 在第 21-29 行；子模块 parent 为 `com.ai.coder:aicoder:1.0.0-SNAPSHOT`。
- 三模块 `ModelConfig`/`ModelProvider` 实体与两个 Repository 字段/方法完全一致；**仅 registry + 自身定义引用它们，无其它文件**（删除安全）。
- chat `getChatModelMap` 被 `ModelController`/`AgentService` 调用——继承基类后签名不变，调用方无需改。
- 各 app 类：chat 有 `@SpringBootApplication @ConfigurationPropertiesScan`；rag/workflow 仅 `@SpringBootApplication`。**均无 `@EntityScan`/`@EnableJpaRepositories`**（依赖默认包扫描）。

---

## File Structure

**新建 `aicoder-core`（`com.ai.coder.core`）**
- `pom.xml`
- `entity/ModelConfig.java`、`entity/ModelProvider.java`（从三模块合一迁入）
- `repository/ModelConfigRepository.java`、`repository/ModelProviderRepository.java`
- `registry/AbstractDynamicModelRegistry.java`（基类：init + createChatModel + probeOllama + registerExtra 钩子 + getChatModel/getChatModelMap）
- Test: `registry/AbstractDynamicModelRegistryTest.java`

**改 `aicoder-chat`**
- Modify `pom.xml`（加 common 依赖）
- Modify `registry/DynamicModelRegistry.java`（改为继承基类 + 仅留 getAvailableChatModels）
- Modify `AicoderChatApplication.java`（加 `@EntityScan`/`@EnableJpaRepositories`）
- Delete `entity/ModelConfig.java`、`entity/ModelProvider.java`、`repository/ModelConfigRepository.java`、`repository/ModelProviderRepository.java`

**改 `aicoder-rag` / `aicoder-workflow`**（同构）
- Modify `pom.xml`、`registry/DynamicModelRegistry.java`（继承 + embedding 钩子 + getEmbeddingModel）、各 app 类（实体扫描）
- Delete 各自 4 个本地实体/repo

---

## Task 1: 创建 aicoder-core 模块（pom + 实体 + Repository）

**Files:**
- Modify: `pom.xml`（父，加 module）
- Create: `aicoder-core/pom.xml`
- Create: `aicoder-core/src/main/java/com/ai/coder/common/entity/ModelConfig.java`
- Create: `aicoder-core/src/main/java/com/ai/coder/common/entity/ModelProvider.java`
- Create: `aicoder-core/src/main/java/com/ai/coder/common/repository/ModelConfigRepository.java`
- Create: `aicoder-core/src/main/java/com/ai/coder/common/repository/ModelProviderRepository.java`

- [ ] **Step 1: 父 pom 加 module**

在 `pom.xml` 的 `<modules>` 块（第 21-29 行）末尾加一行：
```xml
        <module>aicoder-skill</module>
        <module>aicoder-core</module>
    </modules>
```

- [ ] **Step 2: 创建 aicoder-core/pom.xml**

Create `aicoder-core/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.coder</groupId>
        <artifactId>aicoder</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>aicoder-core</artifactId>
    <name>AI Coder AI Common</name>
    <description>共享模型注册表基类与实体（chat/rag/workflow 复用）</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-ollama</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-deepseek</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 迁入实体 ModelConfig**

Create `aicoder-core/src/main/java/com/ai/coder/common/entity/ModelConfig.java`（字段照搬现有）:
```java
package com.ai.coder.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_model_config")
public class ModelConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long providerId;
    @Column(nullable = false, length = 100)
    private String displayName;
    @Column(nullable = false, length = 100)
    private String modelCode;
    @Column(nullable = false, length = 20)
    private String modelType;
    @Column(nullable = false)
    private Integer enabled = 1;
    @Column(nullable = false)
    private Integer sort = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 迁入实体 ModelProvider**

Create `aicoder-core/src/main/java/com/ai/coder/common/entity/ModelProvider.java`:
```java
package com.ai.coder.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_model_provider")
public class ModelProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(length = 200)
    private String logo;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 500)
    private String baseUrl;
    @Column(length = 500)
    private String apiKey;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private Integer enabled = 1;
    @Column(nullable = false)
    private Integer sort = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: 迁入两个 Repository**

Create `aicoder-core/src/main/java/com/ai/coder/common/repository/ModelConfigRepository.java`:
```java
package com.ai.coder.core.repository;

import com.ai.coder.core.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByEnabledOrderBySortAsc(Integer enabled);
}
```

Create `aicoder-core/src/main/java/com/ai/coder/common/repository/ModelProviderRepository.java`:
```java
package com.ai.coder.core.repository;

import com.ai.coder.core.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    List<ModelProvider> findByEnabledOrderBySortAsc(Integer enabled);
}
```

- [ ] **Step 6: 验证 common 模块编译**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 7: 提交**
```bash
git add pom.xml aicoder-core
git commit -m "feat(common): 新建 aicoder-core 模块（共享 ModelConfig/ModelProvider 实体+Repository）"
```

---

## Task 2: AbstractDynamicModelRegistry 基类 + 单测

**Files:**
- Create: `aicoder-core/src/main/java/com/ai/coder/common/registry/AbstractDynamicModelRegistry.java`
- Test: `aicoder-core/src/test/java/com/ai/coder/common/registry/AbstractDynamicModelRegistryTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-core/src/test/java/com/ai/coder/common/registry/AbstractDynamicModelRegistryTest.java`:
```java
package com.ai.coder.core.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractDynamicModelRegistryTest {

    @Test
    void init_registers_chat_models_and_resolves_them() {
        // 用 DEEPSEEK（非 OLLAMA）→ 探针跳过，测试不依赖 Ollama 运行，保持 hermetic
        ModelProvider deepseek = new ModelProvider();
        deepseek.setId(1L);
        deepseek.setCode("DEEPSEEK");
        deepseek.setApiKey("sk-test");
        deepseek.setBaseUrl("https://api.deepseek.com");

        ModelConfig cfg = new ModelConfig();
        cfg.setProviderId(1L);
        cfg.setModelCode("deepseek-test");
        cfg.setDisplayName("Test");
        cfg.setModelType("CHAT");

        ModelConfigRepository modelConfigRepo = mock(ModelConfigRepository.class);
        ModelProviderRepository providerRepo = mock(ModelProviderRepository.class);
        when(providerRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(deepseek));
        when(modelConfigRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(cfg));

        AbstractDynamicModelRegistry registry = new AbstractDynamicModelRegistry(modelConfigRepo, providerRepo) {
        };
        registry.init(); // @PostConstruct 在纯单测不自动触发，手动调用

        ChatModel resolved = registry.getChatModel("deepseek-test");
        assertNotNull(resolved, "init 应注册 deepseek-test");
        assertThrows(IllegalArgumentException.class, () -> registry.getChatModel("nope"),
                "未注册的模型应抛 IllegalArgumentException");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core test -Dtest=AbstractDynamicModelRegistryTest
```
Expected: 编译失败（`AbstractDynamicModelRegistry` 不存在）。

- [ ] **Step 3: 实现基类**

Create `aicoder-core/src/main/java/com/ai/coder/common/registry/AbstractDynamicModelRegistry.java`:
```java
package com.ai.coder.core.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 模型注册表基类：承载 chat/rag/workflow 三模块共享的 Chat 模型构建 + 启动探针逻辑。
 * 子类按需重写 {@link #registerExtra} 建额外模型（如 embedding）。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDynamicModelRegistry {

    protected final ModelConfigRepository modelConfigRepository;
    protected final ModelProviderRepository providerRepository;

    protected final Map<String, ChatModel> chatModels = new HashMap<>();

    private RestClient.Builder deepSeekRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);
        return RestClient.builder().requestFactory(factory);
    }

    @PostConstruct
    public void init() {
        List<ModelProvider> providers = providerRepository.findByEnabledOrderBySortAsc(1);
        List<ModelConfig> models = modelConfigRepository.findByEnabledOrderBySortAsc(1);

        Map<Long, ModelProvider> providerMap = new HashMap<>();
        for (ModelProvider p : providers) {
            providerMap.put(p.getId(), p);
        }

        for (ModelConfig model : models) {
            if (!"CHAT".equals(model.getModelType())) continue;
            ModelProvider provider = providerMap.get(model.getProviderId());
            if (provider == null) continue;

            ChatModel chatModel = createChatModel(provider, model.getModelCode());
            if (chatModel != null) {
                chatModels.put(model.getModelCode(), chatModel);
                log.info("注册 Chat 模型: {} ({})", model.getDisplayName(), model.getModelCode());
                if ("OLLAMA".equals(provider.getCode())) {
                    probeOllama(provider.getBaseUrl(), model.getDisplayName(), model.getModelCode());
                }
            }
        }
        // 钩子：子类可在此建额外模型（如 embedding）
        registerExtra(models, providerMap);
        log.info("DynamicModelRegistry 初始化完成，共 {} 个 Chat 模型", chatModels.size());
    }

    protected ChatModel createChatModel(ModelProvider provider, String modelCode) {
        return switch (provider.getCode()) {
            case "OLLAMA" -> OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(provider.getBaseUrl()).build())
                    .defaultOptions(OllamaChatOptions.builder().model(modelCode).build())
                    .build();
            case "DEEPSEEK" -> DeepSeekChatModel.builder()
                    .deepSeekApi(DeepSeekApi.builder()
                            .apiKey(provider.getApiKey())
                            .restClientBuilder(deepSeekRestClientBuilder())
                            .build())
                    .defaultOptions(DeepSeekChatOptions.builder()
                            .model(modelCode)
                            .maxTokens(8192)
                            .build())
                    .build();
            default -> {
                log.warn("不支持的厂商类型: {}", provider.getCode());
                yield null;
            }
        };
    }

    /**
     * 启动探针：校验 Ollama 可达 + tag 已拉取（仅查 {@code /api/tags}，不加载模型）。失败只 WARN 不阻断。
     */
    @SuppressWarnings("unchecked")
    protected void probeOllama(String baseUrl, String displayName, String modelCode) {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3_000);
            factory.setReadTimeout(5_000);
            Map<String, Object> resp = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(Map.class);
            List<String> pulled = new ArrayList<>();
            if (resp != null && resp.get("models") instanceof List<?> list) {
                for (Object m : list) {
                    if (m instanceof Map<?, ?> mm && mm.get("name") instanceof String n) {
                        pulled.add(n);
                    }
                }
            }
            boolean tagPulled = pulled.stream()
                    .anyMatch(n -> n.equals(modelCode) || n.startsWith(modelCode + ":"));
            if (!tagPulled) {
                log.warn("⚠️ [模型探针] {} ({}) 的 tag 未在 Ollama 拉取列表中。已拉取：{}。请执行 `ollama pull {}`",
                        displayName, modelCode, pulled, modelCode);
            }
        } catch (Exception e) {
            log.warn("⚠️ [模型探针] {} ({}) 不可达：Ollama({}) 连接失败——{}。请确认 Ollama 已运行（`ollama serve`）",
                    displayName, modelCode, baseUrl, e.getMessage());
        }
    }

    /** 子类钩子：CHAT 模型注册后、收尾日志前，建额外模型（如 embedding）。默认空。 */
    protected void registerExtra(List<ModelConfig> models, Map<Long, ModelProvider> providerMap) {
    }

    public ChatModel getChatModel(String modelCode) {
        ChatModel model = chatModels.get(modelCode);
        if (model == null) {
            throw new IllegalArgumentException("不支持的模型: " + modelCode);
        }
        return model;
    }

    public Map<String, ChatModel> getChatModelMap() {
        return Collections.unmodifiableMap(chatModels);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-core test -Dtest=AbstractDynamicModelRegistryTest
```
Expected: `Tests run: 1, Failures: 0`。

- [ ] **Step 5: 提交**
```bash
git add aicoder-core/src/main/java/com/ai/coder/common/registry/AbstractDynamicModelRegistry.java aicoder-core/src/test/java/com/ai/coder/common/registry/AbstractDynamicModelRegistryTest.java
git commit -m "feat(common): AbstractDynamicModelRegistry 基类（init+探针+embedding 钩子）"
```

---

## Task 3: chat 改为继承基类 + 实体扫描 + 删本地实体/repo

**Files:**
- Modify: `aicoder-chat/pom.xml`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/registry/DynamicModelRegistry.java`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java`
- Delete: `aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelConfig.java`
- Delete: `aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelProvider.java`
- Delete: `aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelConfigRepository.java`
- Delete: `aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelProviderRepository.java`

- [ ] **Step 1: chat pom 加 common 依赖**

在 `aicoder-chat/pom.xml` 的 `<dependencies>` 内加：
```xml
        <dependency>
            <groupId>com.ai.coder</groupId>
            <artifactId>aicoder-core</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: chat DynamicModelRegistry 改为继承基类**

整体替换 `aicoder-chat/src/main/java/com/ai/coder/chat/registry/DynamicModelRegistry.java`：
```java
package com.ai.coder.chat.registry;

import com.ai.coder.chat.dto.ModelInfoDTO;
import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chat 模块模型注册表：继承共享基类，仅补充「给前端列出可用 Chat 模型」。
 */
@Component
public class DynamicModelRegistry extends AbstractDynamicModelRegistry {

    public DynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }

    public List<ModelInfoDTO> getAvailableChatModels() {
        List<ModelConfig> models = modelConfigRepository.findByEnabledOrderBySortAsc(1);
        Map<Long, ModelProvider> providerMap = new HashMap<>();
        providerRepository.findByEnabledOrderBySortAsc(1).forEach(p -> providerMap.put(p.getId(), p));

        return models.stream()
                .filter(m -> "CHAT".equals(m.getModelType()))
                .map(m -> {
                    ModelProvider p = providerMap.get(m.getProviderId());
                    return ModelInfoDTO.builder()
                            .modelId(m.getModelCode())
                            .modelName(m.getDisplayName())
                            .provider(p != null ? p.getName() : "未知")
                            .description(p != null ? p.getDescription() : "")
                            .build();
                })
                .toList();
    }
}
```

- [ ] **Step 3: chat app 类加实体扫描**

在 `aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java` 加 import + 注解（与现有 `@SpringBootApplication @ConfigurationPropertiesScan` 并列）：
```java
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
```
注解行改为：
```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
```

- [ ] **Step 4: 删除 chat 本地实体/repo**
```bash
git rm aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelConfig.java \
       aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelProvider.java \
       aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelConfigRepository.java \
       aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelProviderRepository.java
```
（若 entity/repository 目录删空无妨，不要删其它文件。）

- [ ] **Step 5: 编译验证**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core,aicoder-chat -am compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 6: 重启 chat 验证（注册日志 + 探针不变）**

确保 chat 未在跑：`pkill -9 -f AicoderChatApplication 2>/dev/null; sleep 3`
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-chat > /tmp/svc-chat.log 2>&1 &
sleep 60
grep -E "注册 Chat 模型|初始化完成|Not a managed type|ERROR" /tmp/svc-chat.log | grep -viE "netty|DnsServer|MacOS|Aggregation" | tail -6
nc -z localhost 8082 && echo "8082: UP" || echo "8082: down"
```
Expected: 看到 `注册 Chat 模型: ...` 三行 + `初始化完成，共 3 个 Chat 模型`，无 "Not a managed type" / ERROR；8082 UP。停掉：`pkill -9 -f AicoderChatApplication 2>/dev/null`。

- [ ] **Step 7: 提交**
```bash
git add aicoder-chat/pom.xml aicoder-chat/src/main/java/com/ai/coder/chat/registry/DynamicModelRegistry.java aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java
git commit -m "refactor(chat): DynamicModelRegistry 继承 AbstractDynamicModelRegistry + 实体扫描 + 删本地实体/repo"
```

---

## Task 4: rag 改为继承基类 + embedding 钩子 + 实体扫描

**Files:**
- Modify: `aicoder-rag/pom.xml`、`aicoder-rag/src/main/java/com/ai/coder/rag/registry/DynamicModelRegistry.java`、`aicoder-rag/src/main/java/com/ai/coder/rag/AicoderRagApplication.java`
- Delete: `aicoder-rag/.../entity/ModelConfig.java`、`entity/ModelProvider.java`、`repository/ModelConfigRepository.java`、`repository/ModelProviderRepository.java`

- [ ] **Step 1: rag pom 加 common 依赖**

在 `aicoder-rag/pom.xml` `<dependencies>` 加：
```xml
        <dependency>
            <groupId>com.ai.coder</groupId>
            <artifactId>aicoder-core</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: rag DynamicModelRegistry 改为继承基类 + embedding 钩子**

整体替换 `aicoder-rag/src/main/java/com/ai/coder/rag/registry/DynamicModelRegistry.java`：
```java
package com.ai.coder.rag.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * rag 模块模型注册表：继承共享基类，重写 registerExtra 建 embedding（首个 EMBEDDING 模型）。
 */
@Slf4j
@Component
public class DynamicModelRegistry extends AbstractDynamicModelRegistry {

    private EmbeddingModel embeddingModel;

    public DynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }

    @Override
    protected void registerExtra(List<ModelConfig> models, Map<Long, ModelProvider> providerMap) {
        if (embeddingModel != null) return;
        for (ModelConfig model : models) {
            if (!"EMBEDDING".equals(model.getModelType())) continue;
            ModelProvider provider = providerMap.get(model.getProviderId());
            if (provider == null || !"OLLAMA".equals(provider.getCode())) continue;
            embeddingModel = OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(provider.getBaseUrl()).build())
                    .defaultOptions(OllamaEmbeddingOptions.builder().model(model.getModelCode()).build())
                    .build();
            log.info("注册 Embedding 模型: {} ({})", model.getDisplayName(), model.getModelCode());
            probeOllama(provider.getBaseUrl(), model.getDisplayName(), model.getModelCode());
            return; // 首个 EMBEDDING 模型即用（沿用现状）
        }
    }

    public EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            throw new IllegalStateException("没有可用的 Embedding 模型，请在管理页面配置");
        }
        return embeddingModel;
    }
}
```

- [ ] **Step 3: rag app 类加实体扫描**

在 `aicoder-rag/src/main/java/com/ai/coder/rag/AicoderRagApplication.java`（现仅 `@SpringBootApplication`）加 import + 注解：
```java
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
```
```java
@SpringBootApplication
@EntityScan(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
```

- [ ] **Step 4: 删除 rag 本地实体/repo**
```bash
git rm aicoder-rag/src/main/java/com/ai/coder/rag/entity/ModelConfig.java \
       aicoder-rag/src/main/java/com/ai/coder/rag/entity/ModelProvider.java \
       aicoder-rag/src/main/java/com/ai/coder/rag/repository/ModelConfigRepository.java \
       aicoder-rag/src/main/java/com/ai/coder/rag/repository/ModelProviderRepository.java
```
（勿删 rag 的 KnowledgeBase / VectorDbConfig 等其它实体/repo。）

- [ ] **Step 5: 编译验证**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core,aicoder-rag -am compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 6: 重启 rag 验证（Chat + Embedding 都注册）**
```bash
pkill -9 -f AicoderRagApplication 2>/dev/null; sleep 3
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-rag > /tmp/svc-rag.log 2>&1 &
sleep 60
grep -E "注册 (Chat|Embedding) 模型|初始化完成|Not a managed type|ERROR" /tmp/svc-rag.log | grep -viE "netty|DnsServer|MacOS" | tail -8
nc -z localhost 8083 && echo "8083: UP" || echo "8083: down"
```
Expected: 看到 Chat 模型 + Embedding 模型都注册 + `初始化完成`，无 "Not a managed type" / ERROR；8083 UP。停掉：`pkill -9 -f AicoderRagApplication 2>/dev/null`。

- [ ] **Step 7: 提交**
```bash
git add aicoder-rag/pom.xml aicoder-rag/src/main/java/com/ai/coder/rag/registry/DynamicModelRegistry.java aicoder-rag/src/main/java/com/ai/coder/rag/AicoderRagApplication.java
git commit -m "refactor(rag): DynamicModelRegistry 继承基类 + embedding 钩子 + 实体扫描 + 删本地实体/repo"
```

---

## Task 5: workflow 改为继承基类 + embedding 钩子 + 实体扫描

**Files:**
- Modify: `aicoder-workflow/pom.xml`、`aicoder-workflow/src/main/java/com/ai/coder/workflow/registry/DynamicModelRegistry.java`、`aicoder-workflow/src/main/java/com/ai/coder/workflow/AicoderWorkflowApplication.java`
- Delete: `aicoder-workflow/.../entity/ModelConfig.java`、`entity/ModelProvider.java`、`repository/ModelConfigRepository.java`、`repository/ModelProviderRepository.java`

- [ ] **Step 1: workflow pom 加 common 依赖**

在 `aicoder-workflow/pom.xml` `<dependencies>` 加：
```xml
        <dependency>
            <groupId>com.ai.coder</groupId>
            <artifactId>aicoder-core</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: workflow DynamicModelRegistry 改为继承基类 + embedding 钩子**

整体替换 `aicoder-workflow/src/main/java/com/ai/coder/workflow/registry/DynamicModelRegistry.java`：
```java
package com.ai.coder.workflow.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * workflow 模块模型注册表：继承共享基类，重写 registerExtra 建 embedding。
 */
@Slf4j
@Component
public class DynamicModelRegistry extends AbstractDynamicModelRegistry {

    private EmbeddingModel embeddingModel;

    public DynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }

    @Override
    protected void registerExtra(List<ModelConfig> models, Map<Long, ModelProvider> providerMap) {
        if (embeddingModel != null) return;
        for (ModelConfig model : models) {
            if (!"EMBEDDING".equals(model.getModelType())) continue;
            ModelProvider provider = providerMap.get(model.getProviderId());
            if (provider == null || !"OLLAMA".equals(provider.getCode())) continue;
            embeddingModel = OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(provider.getBaseUrl()).build())
                    .defaultOptions(OllamaEmbeddingOptions.builder().model(model.getModelCode()).build())
                    .build();
            log.info("注册 Embedding 模型: {} ({})", model.getDisplayName(), model.getModelCode());
            probeOllama(provider.getBaseUrl(), model.getDisplayName(), model.getModelCode());
            return;
        }
    }

    public EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            throw new IllegalStateException("没有可用的 Embedding 模型，请在管理页面配置");
        }
        return embeddingModel;
    }
}
```

- [ ] **Step 3: workflow app 类加实体扫描**

在 `aicoder-workflow/src/main/java/com/ai/coder/workflow/AicoderWorkflowApplication.java`（现仅 `@SpringBootApplication`）加 import + 注解：
```java
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
```
```java
@SpringBootApplication
@EntityScan(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
```

- [ ] **Step 4: 删除 workflow 本地实体/repo**
```bash
git rm aicoder-workflow/src/main/java/com/ai/coder/workflow/entity/ModelConfig.java \
       aicoder-workflow/src/main/java/com/ai/coder/workflow/entity/ModelProvider.java \
       aicoder-workflow/src/main/java/com/ai/coder/workflow/repository/ModelConfigRepository.java \
       aicoder-workflow/src/main/java/com/ai/coder/workflow/repository/ModelProviderRepository.java
```
（勿删 workflow 的 KnowledgeBase / VectorDbConfig 等其它实体/repo。）

- [ ] **Step 5: 编译验证**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core,aicoder-workflow -am compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 6: 重启 workflow 验证**
```bash
pkill -9 -f AicoderWorkflowApplication 2>/dev/null; sleep 3
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-workflow > /tmp/svc-workflow.log 2>&1 &
sleep 60
grep -E "注册 (Chat|Embedding) 模型|初始化完成|Not a managed type|ERROR" /tmp/svc-workflow.log | grep -viE "netty|DnsServer|MacOS" | tail -8
nc -z localhost 8084 && echo "8084: UP" || echo "8084: down"
```
Expected: Chat + Embedding 都注册 + 初始化完成，无 ERROR；8084 UP。停掉：`pkill -9 -f AicoderWorkflowApplication 2>/dev/null`。

- [ ] **Step 7: 提交**
```bash
git add aicoder-workflow/pom.xml aicoder-workflow/src/main/java/com/ai/coder/workflow/registry/DynamicModelRegistry.java aicoder-workflow/src/main/java/com/ai/coder/workflow/AicoderWorkflowApplication.java
git commit -m "refactor(workflow): DynamicModelRegistry 继承基类 + embedding 钩子 + 实体扫描 + 删本地实体/repo"
```

---

## Task 6: 全量验证（探针 + 功能回归）

**Files:** 无代码改动——验证清单。需中间件运行（MySQL/Redis/Nacos，Ollama 可选但探针对 Ollama 模型有意义）。

- [ ] **Step 1: 全模块编译**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q clean compile -DskipTests
```
Expected: BUILD SUCCESS（含 aicoder-core + 三模块）。

- [ ] **Step 2: common 单测 + 三模块现有测试回归**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-core,aicoder-chat,aicoder-rag,aicoder-workflow -am test
```
Expected: 全绿（含新的 AbstractDynamicModelRegistryTest）。

- [ ] **Step 3: 启动三模块，确认探针在基类里仍生效**

启动 chat/rag/workflow（连同 gateway/system/admin 以便功能回归）。在各自日志确认：
- `注册 Chat 模型: ...`（chat 3 个、rag/workflow 各 3 个）
- rag/workflow `注册 Embedding 模型: ...`
- `DynamicModelRegistry 初始化完成`
- Ollama 模型（gemma3:latest / deepseek-coder:6.7b）若已拉取则探针静默；若 Ollama 未运行则见 `⚠️ [模型探针] ... 不可达`（预期、不阻断）。

- [ ] **Step 4: 探针 WARN 路径复验（假模型）**

向 DB 临时插一个假的 Ollama CHAT 模型，重启 chat，确认基类探针触发 WARN，再清理：
```bash
docker exec mysql mysql -uroot -proot test_ai -e "INSERT INTO ai_model_config (provider_id, model_code, display_name, model_type, enabled, sort) VALUES (1, 'zzz-fake-probe:latest', '探针复验', 'CHAT', 1, 99);"
# 重启 chat（见各任务的启动方式），日志应见：⚠️ [模型探针] 探针复验 (zzz-fake-probe:latest) 的 tag 未在 Ollama 拉取列表中...
docker exec mysql mysql -uroot -proot test_ai -e "DELETE FROM ai_model_config WHERE model_code='zzz-fake-probe:latest';"
```

- [ ] **Step 5: 功能回归**

- chat：发一条普通对话（用 deepseek-v4-flash），确认模型解析 + 回复正常（getChatModelMap 调用方 ModelController/AgentService 不受影响）。
- rag：做一次 RAG 检索（用 embedding），确认 embedding 解析正常（rag 的 getEmbeddingModel 经子类提供）。

- [ ] **Step 6: 记录结论**

把验证结论（哪些通过/异常）记录。停服务清理。

---

## 完成后

使用 superpowers:finishing-a-development-branch 收尾（全模块编译 + 测试绿 → 呈现选项）。本轮提交默认留本地 main、不推送。
