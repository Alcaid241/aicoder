# 模型管理与动态模型加载 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增厂商配置 + 模型配置 + 向量库配置管理页面，并重构 chat/rag/workflow 从数据库动态加载 ChatModel 和 EmbeddingModel。

**Architecture:** admin 模块新增 Provider/ModelConfig CRUD API 和初始数据。chat/rag/workflow 新增 DynamicModelRegistry，在 @PostConstruct 时从数据库读取配置并手动创建 Spring AI 的 ChatModel/EmbeddingModel 实例。YAML 仅保留 base-url/api-key，移除 model options。前端新增厂商配置页、模型配置页，迁移向量库配置页到模型管理目录下。

**Tech Stack:** Spring Boot 3.5.13, Spring AI 1.1.2, JPA, Vue 3, TypeScript, Pinia

**Spec:** `docs/superpowers/specs/2026-06-05-model-management-design.md`

---

## File Structure

### admin 模块 — 新增
| 文件 | 职责 |
|------|------|
| `entity/ModelProvider.java` | 厂商实体，映射 ai_model_provider |
| `entity/ModelConfig.java` | 模型配置实体，映射 ai_model_config |
| `repository/ModelProviderRepository.java` | 厂商 Repository |
| `repository/ModelConfigRepository.java` | 模型配置 Repository |
| `dto/ProviderDTO.java` | 厂商响应 DTO |
| `dto/CreateProviderRequest.java` | 厂商创建/编辑请求 DTO |
| `dto/ModelConfigDTO.java` | 模型响应 DTO（含 providerName） |
| `dto/CreateModelConfigRequest.java` | 模型创建/编辑请求 DTO |
| `service/ModelProviderService.java` | 厂商 CRUD 服务 |
| `service/ModelConfigService.java` | 模型 CRUD 服务 |
| `controller/ModelProviderController.java` | 厂商 REST API |
| `controller/ModelConfigController.java` | 模型 REST API |
| `init/ModelDataInitializer.java` | 厂商和模型初始数据 |

### admin 模块 — 修改
| 文件 | 改动 |
|------|------|
| 无其他改动 | admin 已有 JPA + MySQL 依赖，无需改 pom |

### chat 模块 — 新增
| 文件 | 职责 |
|------|------|
| `entity/ModelProvider.java` | 厂商只读实体 |
| `entity/ModelConfig.java` | 模型只读实体 |
| `repository/ModelProviderRepository.java` | 厂商只读 Repository |
| `repository/ModelConfigRepository.java` | 模型只读 Repository |
| `registry/DynamicModelRegistry.java` | 动态模型注册中心 |

### chat 模块 — 修改/删除
| 文件 | 改动 |
|------|------|
| `config/ChatModelConfig.java` | **删除** — 被 DynamicModelRegistry 替代 |
| `service/ModelService.java` | **删除** — 硬编码模型列表不再需要 |
| `service/ChatService.java` | 改为注入 DynamicModelRegistry |
| `service/AgentService.java` | 改为注入 DynamicModelRegistry |
| `controller/ModelController.java` | 改为从 DynamicModelRegistry 获取模型列表 |
| `resources/application.yml` | 保留 base-url/api-key，移除 chat.options |

### rag 模块 — 新增
| 文件 | 职责 |
|------|------|
| `entity/ModelProvider.java` | 厂商只读实体 |
| `entity/ModelConfig.java` | 模型只读实体 |
| `repository/ModelProviderRepository.java` | 厂商只读 Repository |
| `repository/ModelConfigRepository.java` | 模型只读 Repository |
| `registry/DynamicModelRegistry.java` | 动态模型注册中心 |

### rag 模块 — 修改/删除
| 文件 | 改动 |
|------|------|
| `config/ChatModelConfig.java` | **删除** |
| `service/RagChatService.java` | 改为注入 DynamicModelRegistry |
| `service/SqlGenerationService.java` | 改为注入 DynamicModelRegistry |
| `config/DynamicVectorStoreConfig.java` | 从 DynamicModelRegistry 获取 EmbeddingModel |
| `resources/application.yml` | 保留 base-url/api-key，移除 chat/embedding options |

### workflow 模块 — 新增
| 文件 | 职责 |
|------|------|
| `model/entity/ModelProvider.java` | 厂商只读实体 |
| `model/entity/ModelConfig.java` | 模型只读实体 |
| `repository/ModelProviderRepository.java` | 厂商只读 Repository |
| `repository/ModelConfigRepository.java` | 模型只读 Repository |
| `registry/DynamicModelRegistry.java` | 动态模型注册中心 |

### workflow 模块 — 修改/删除
| 文件 | 改动 |
|------|------|
| `config/WorkflowModelConfig.java` | **删除** |
| `service/WorkflowExecutionService.java` | 改为注入 DynamicModelRegistry |
| `service/WorkflowNodeFactory.java` | 改为注入 DynamicModelRegistry |
| `config/DynamicVectorStoreConfig.java` | 从 DynamicModelRegistry 获取 EmbeddingModel |
| `resources/application.yml` | 保留 base-url/api-key，移除 chat/embedding options |

### system 模块 — 修改
| 文件 | 改动 |
|------|------|
| `init/DataInitializer.java` | 菜单结构调整：新增模型管理目录，移除原向量库一级菜单 |

### gateway 模块 — 修改
| 文件 | 改动 |
|------|------|
| `filter/JwtAuthFilter.java` | 无需改动（model API 在 admin 下，走认证） |

### 前端 — 新增
| 文件 | 职责 |
|------|------|
| `src/api/model.ts` | 厂商和模型配置 API |
| `src/views/model/ProviderConfigView.vue` | 厂商配置页面 |
| `src/views/model/ModelConfigView.vue` | 模型配置页面 |

### 前端 — 修改
| 文件 | 改动 |
|------|------|
| `src/views/admin/VectorDbConfigView.vue` → `src/views/model/VectorDbConfigView.vue` | 迁移到模型管理目录下 |
| `src/router/index.ts` | 新增模型管理路由，移除旧 vectordb 路由 |
| `src/types/index.ts` | 新增 Provider/ModelConfig 类型 |

---

## Task 1: Admin — Entity + Repository

**Files:**
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/entity/ModelProvider.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/entity/ModelConfig.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/repository/ModelProviderRepository.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/repository/ModelConfigRepository.java`

- [ ] **Step 1: Create ModelProvider entity**

`aicoder-admin/src/main/java/com/ai/coder/admin/entity/ModelProvider.java`:
```java
package com.ai.coder.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

- [ ] **Step 2: Create ModelConfig entity**

`aicoder-admin/src/main/java/com/ai/coder/admin/entity/ModelConfig.java`:
```java
package com.ai.coder.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

- [ ] **Step 3: Create repositories**

`aicoder-admin/src/main/java/com/ai/coder/admin/repository/ModelProviderRepository.java`:
```java
package com.ai.coder.admin.repository;

import com.ai.coder.admin.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    Optional<ModelProvider> findByCode(String code);
    boolean existsByCode(String code);
    List<ModelProvider> findByEnabledOrderBySortAsc(Integer enabled);
}
```

`aicoder-admin/src/main/java/com/ai/coder/admin/repository/ModelConfigRepository.java`:
```java
package com.ai.coder.admin.repository;

import com.ai.coder.admin.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByProviderIdOrderBySortAsc(Long providerId);
    List<ModelConfig> findByModelTypeAndEnabledOrderBySortAsc(String modelType, Integer enabled);
    List<ModelConfig> findByEnabledOrderBySortAsc(Integer enabled);
    boolean existsByProviderId(Long providerId);
}
```

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -pl aicoder-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add aicoder-admin/src/main/java/com/ai/coder/admin/entity/ModelProvider.java \
        aicoder-admin/src/main/java/com/ai/coder/admin/entity/ModelConfig.java \
        aicoder-admin/src/main/java/com/ai/coder/admin/repository/ModelProviderRepository.java \
        aicoder-admin/src/main/java/com/ai/coder/admin/repository/ModelConfigRepository.java
git commit -m "feat(admin): 新增 ModelProvider/ModelConfig 实体和 Repository"
```

---

## Task 2: Admin — DTO + Service + Controller

**Files:**
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/ProviderDTO.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/CreateProviderRequest.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/ModelConfigDTO.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/CreateModelConfigRequest.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/service/ModelProviderService.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/service/ModelConfigService.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelProviderController.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelConfigController.java`

- [ ] **Step 1: Create DTOs**

`aicoder-admin/src/main/java/com/ai/coder/admin/dto/ProviderDTO.java`:
```java
package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDTO {
    private Long id;
    private String name;
    private String logo;
    private String code;
    private String baseUrl;
    private String apiKey;
    private String description;
    private Integer enabled;
    private Integer sort;
}
```

`aicoder-admin/src/main/java/com/ai/coder/admin/dto/CreateProviderRequest.java`:
```java
package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProviderRequest {
    private String name;
    private String logo;
    private String code;
    private String baseUrl;
    private String apiKey;
    private String description;
    private Integer enabled;
    private Integer sort;
}
```

`aicoder-admin/src/main/java/com/ai/coder/admin/dto/ModelConfigDTO.java`:
```java
package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDTO {
    private Long id;
    private Long providerId;
    private String providerName;
    private String displayName;
    private String modelCode;
    private String modelType;
    private Integer enabled;
    private Integer sort;
}
```

`aicoder-admin/src/main/java/com/ai/coder/admin/dto/CreateModelConfigRequest.java`:
```java
package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateModelConfigRequest {
    private Long providerId;
    private String displayName;
    private String modelCode;
    private String modelType;
    private Integer enabled;
    private Integer sort;
}
```

- [ ] **Step 2: Create ModelProviderService**

`aicoder-admin/src/main/java/com/ai/coder/admin/service/ModelProviderService.java`:
```java
package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.CreateProviderRequest;
import com.ai.coder.admin.dto.ProviderDTO;
import com.ai.coder.admin.entity.ModelProvider;
import com.ai.coder.admin.repository.ModelConfigRepository;
import com.ai.coder.admin.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelProviderService {

    private final ModelProviderRepository providerRepository;
    private final ModelConfigRepository modelConfigRepository;

    public List<ProviderDTO> listProviders() {
        return providerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ProviderDTO getById(Long id) {
        return toDTO(providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("厂商不存在")));
    }

    @Transactional
    public ProviderDTO create(CreateProviderRequest request) {
        if (providerRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("厂商编码已存在: " + request.getCode());
        }
        ModelProvider provider = new ModelProvider();
        provider.setName(request.getName());
        provider.setLogo(request.getLogo());
        provider.setCode(request.getCode());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setApiKey(request.getApiKey());
        provider.setDescription(request.getDescription());
        provider.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        provider.setSort(request.getSort() != null ? request.getSort() : 0);
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        return toDTO(providerRepository.save(provider));
    }

    @Transactional
    public ProviderDTO update(Long id, CreateProviderRequest request) {
        ModelProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("厂商不存在"));
        if (request.getName() != null) provider.setName(request.getName());
        if (request.getLogo() != null) provider.setLogo(request.getLogo());
        if (request.getBaseUrl() != null) provider.setBaseUrl(request.getBaseUrl());
        if (request.getApiKey() != null) provider.setApiKey(request.getApiKey());
        if (request.getDescription() != null) provider.setDescription(request.getDescription());
        if (request.getEnabled() != null) provider.setEnabled(request.getEnabled());
        if (request.getSort() != null) provider.setSort(request.getSort());
        provider.setUpdatedAt(LocalDateTime.now());
        return toDTO(providerRepository.save(provider));
    }

    @Transactional
    public void delete(Long id) {
        if (modelConfigRepository.existsByProviderId(id)) {
            throw new RuntimeException("该厂商下存在关联模型，无法删除");
        }
        providerRepository.deleteById(id);
    }

    private ProviderDTO toDTO(ModelProvider p) {
        return new ProviderDTO(p.getId(), p.getName(), p.getLogo(), p.getCode(),
                p.getBaseUrl(), p.getApiKey(), p.getDescription(), p.getEnabled(), p.getSort());
    }
}
```

- [ ] **Step 3: Create ModelConfigService**

`aicoder-admin/src/main/java/com/ai/coder/admin/service/ModelConfigService.java`:
```java
package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.CreateModelConfigRequest;
import com.ai.coder.admin.dto.ModelConfigDTO;
import com.ai.coder.admin.entity.ModelConfig;
import com.ai.coder.admin.entity.ModelProvider;
import com.ai.coder.admin.repository.ModelConfigRepository;
import com.ai.coder.admin.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final ModelProviderRepository providerRepository;

    public List<ModelConfigDTO> listModels(String modelType) {
        List<ModelConfig> models;
        if (modelType != null && !modelType.isBlank()) {
            models = modelConfigRepository.findByModelTypeAndEnabledOrderBySortAsc(modelType, 1);
        } else {
            models = modelConfigRepository.findByEnabledOrderBySortAsc(1);
        }
        return models.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ModelConfigDTO> listAllModels() {
        return modelConfigRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ModelConfigDTO getById(Long id) {
        return toDTO(modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在")));
    }

    @Transactional
    public ModelConfigDTO create(CreateModelConfigRequest request) {
        ModelProvider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new RuntimeException("厂商不存在"));
        ModelConfig config = new ModelConfig();
        config.setProviderId(provider.getId());
        config.setDisplayName(request.getDisplayName());
        config.setModelCode(request.getModelCode());
        config.setModelType(request.getModelType());
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        config.setSort(request.getSort() != null ? request.getSort() : 0);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return toDTO(modelConfigRepository.save(config));
    }

    @Transactional
    public ModelConfigDTO update(Long id, CreateModelConfigRequest request) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
        if (request.getProviderId() != null) config.setProviderId(request.getProviderId());
        if (request.getDisplayName() != null) config.setDisplayName(request.getDisplayName());
        if (request.getModelCode() != null) config.setModelCode(request.getModelCode());
        if (request.getModelType() != null) config.setModelType(request.getModelType());
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getSort() != null) config.setSort(request.getSort());
        config.setUpdatedAt(LocalDateTime.now());
        return toDTO(modelConfigRepository.save(config));
    }

    @Transactional
    public void delete(Long id) {
        modelConfigRepository.deleteById(id);
    }

    private ModelConfigDTO toDTO(ModelConfig c) {
        String providerName = providerRepository.findById(c.getProviderId())
                .map(ModelProvider::getName).orElse("未知");
        return new ModelConfigDTO(c.getId(), c.getProviderId(), providerName,
                c.getDisplayName(), c.getModelCode(), c.getModelType(), c.getEnabled(), c.getSort());
    }
}
```

- [ ] **Step 4: Create Controllers**

`aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelProviderController.java`:
```java
package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.CreateProviderRequest;
import com.ai.coder.admin.dto.ProviderDTO;
import com.ai.coder.admin.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model/provider")
@RequiredArgsConstructor
public class ModelProviderController {

    private final ModelProviderService providerService;

    @GetMapping("/list")
    public List<ProviderDTO> list() {
        return providerService.listProviders();
    }

    @GetMapping("/{id}")
    public ProviderDTO getById(@PathVariable Long id) {
        return providerService.getById(id);
    }

    @PostMapping
    public ProviderDTO create(@RequestBody CreateProviderRequest request) {
        return providerService.create(request);
    }

    @PutMapping("/{id}")
    public ProviderDTO update(@PathVariable Long id, @RequestBody CreateProviderRequest request) {
        return providerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        providerService.delete(id);
    }
}
```

`aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelConfigController.java`:
```java
package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.CreateModelConfigRequest;
import com.ai.coder.admin.dto.ModelConfigDTO;
import com.ai.coder.admin.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model/config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/list")
    public List<ModelConfigDTO> list(@RequestParam(required = false) String modelType) {
        if (modelType != null) {
            return modelConfigService.listModels(modelType);
        }
        return modelConfigService.listAllModels();
    }

    @GetMapping("/{id}")
    public ModelConfigDTO getById(@PathVariable Long id) {
        return modelConfigService.getById(id);
    }

    @PostMapping
    public ModelConfigDTO create(@RequestBody CreateModelConfigRequest request) {
        return modelConfigService.create(request);
    }

    @PutMapping("/{id}")
    public ModelConfigDTO update(@PathVariable Long id, @RequestBody CreateModelConfigRequest request) {
        return modelConfigService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        modelConfigService.delete(id);
    }
}
```

- [ ] **Step 5: Compile and verify**

Run: `mvn compile -pl aicoder-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add aicoder-admin/src/main/java/com/ai/coder/admin/dto/ \
        aicoder-admin/src/main/java/com/ai/coder/admin/service/ModelProviderService.java \
        aicoder-admin/src/main/java/com/ai/coder/admin/service/ModelConfigService.java \
        aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelProviderController.java \
        aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelConfigController.java
git commit -m "feat(admin): 新增厂商配置和模型配置的 DTO/Service/Controller"
```

---

## Task 3: Admin — 初始数据 (ModelDataInitializer)

**Files:**
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/init/ModelDataInitializer.java`

- [ ] **Step 1: Create ModelDataInitializer**

`aicoder-admin/src/main/java/com/ai/coder/admin/init/ModelDataInitializer.java`:
```java
package com.ai.coder.admin.init;

import com.ai.coder.admin.entity.ModelConfig;
import com.ai.coder.admin.entity.ModelProvider;
import com.ai.coder.admin.repository.ModelConfigRepository;
import com.ai.coder.admin.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class ModelDataInitializer implements CommandLineRunner {

    private final ModelProviderRepository providerRepository;
    private final ModelConfigRepository modelConfigRepository;

    @Override
    public void run(String... args) {
        initProviders();
        initModels();
    }

    private void initProviders() {
        if (providerRepository.findByCode("OLLAMA").isPresent()) {
            return;
        }
        saveProvider("Ollama", null, "OLLAMA",
                "http://localhost:11434", null,
                "本地 Ollama 推理服务", 0);
        saveProvider("DeepSeek", null, "DEEPSEEK",
                "https://api.deepseek.com",
                "${DEEPSEEK_API_KEY}",
                "DeepSeek 云端 API 服务", 1);
        log.info("初始化厂商数据");
    }

    private void initModels() {
        if (modelConfigRepository.count() > 0) {
            return;
        }
        ModelProvider ollama = providerRepository.findByCode("OLLAMA").orElseThrow();
        ModelProvider deepseek = providerRepository.findByCode("DEEPSEEK").orElseThrow();

        saveModel(ollama.getId(), "Gemma 3 4B", "gemma3:4b", "CHAT", 0);
        saveModel(ollama.getId(), "DeepSeek Coder", "deepseek-coder", "CHAT", 1);
        saveModel(deepseek.getId(), "DeepSeek V4 Flash", "deepseek-v4-flash", "CHAT", 2);
        saveModel(ollama.getId(), "Nomic Embed Text", "nomic-embed-text", "EMBEDDING", 0);
        log.info("初始化模型配置数据");
    }

    private void saveProvider(String name, String logo, String code,
                              String baseUrl, String apiKey, String description, int sort) {
        ModelProvider p = new ModelProvider();
        p.setName(name);
        p.setLogo(logo);
        p.setCode(code);
        p.setBaseUrl(baseUrl);
        p.setApiKey(apiKey);
        p.setDescription(description);
        p.setEnabled(1);
        p.setSort(sort);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        providerRepository.save(p);
    }

    private void saveModel(Long providerId, String displayName,
                           String modelCode, String modelType, int sort) {
        ModelConfig m = new ModelConfig();
        m.setProviderId(providerId);
        m.setDisplayName(displayName);
        m.setModelCode(modelCode);
        m.setModelType(modelType);
        m.setEnabled(1);
        m.setSort(sort);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        modelConfigRepository.save(m);
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -pl aicoder-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-admin/src/main/java/com/ai/coder/admin/init/ModelDataInitializer.java
git commit -m "feat(admin): 新增厂商和模型初始数据 (Ollama + DeepSeek)"
```

---

## Task 4: Chat 模块 — DynamicModelRegistry + 重构

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelProvider.java`
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelConfig.java`
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelProviderRepository.java`
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelConfigRepository.java`
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/registry/DynamicModelRegistry.java`
- Delete: `aicoder-chat/src/main/java/com/ai/coder/chat/config/ChatModelConfig.java`
- Delete: `aicoder-chat/src/main/java/com/ai/coder/chat/service/ModelService.java`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/service/AgentService.java`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/controller/ModelController.java`
- Modify: `aicoder-chat/src/main/resources/application.yml`

- [ ] **Step 1: Create read-only entities**

`aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelProvider.java` — 与 admin 实体字段一致，映射同一张表 `ai_model_provider`。使用 `@Data @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "ai_model_provider")`，字段：`id, name, logo, code, baseUrl, apiKey, description, enabled, sort, createdAt, updatedAt`。

`aicoder-chat/src/main/java/com/ai/coder/chat/entity/ModelConfig.java` — 映射 `ai_model_config`。字段：`id, providerId, displayName, modelCode, modelType, enabled, sort, createdAt, updatedAt`。

> 实现代码与 Task 1 中的实体完全相同，只是 package 改为 `com.ai.coder.chat.entity`。

- [ ] **Step 2: Create read-only repositories**

`aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelProviderRepository.java`:
```java
package com.ai.coder.chat.repository;

import com.ai.coder.chat.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    List<ModelProvider> findByEnabledOrderBySortAsc(Integer enabled);
}
```

`aicoder-chat/src/main/java/com/ai/coder/chat/repository/ModelConfigRepository.java`:
```java
package com.ai.coder.chat.repository;

import com.ai.coder.chat.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByEnabledOrderBySortAsc(Integer enabled);
}
```

- [ ] **Step 3: Create DynamicModelRegistry**

`aicoder-chat/src/main/java/com/ai/coder/chat/registry/DynamicModelRegistry.java`:
```java
package com.ai.coder.chat.registry;

import com.ai.coder.chat.dto.ModelInfoDTO;
import com.ai.coder.chat.entity.ModelConfig;
import com.ai.coder.chat.entity.ModelProvider;
import com.ai.coder.chat.repository.ModelConfigRepository;
import com.ai.coder.chat.repository.ModelProviderRepository;
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
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicModelRegistry {

    private final ModelProviderRepository providerRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final OllamaApi ollamaApi;
    private final DeepSeekApi deepSeekApi;

    private final Map<String, ChatModel> chatModels = new HashMap<>();

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
            }
        }
        log.info("DynamicModelRegistry 初始化完成，共 {} 个 Chat 模型", chatModels.size());
    }

    private ChatModel createChatModel(ModelProvider provider, String modelCode) {
        return switch (provider.getCode()) {
            case "OLLAMA" -> OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(OllamaChatOptions.builder().model(modelCode).build())
                    .build();
            case "DEEPSEEK" -> DeepSeekChatModel.builder()
                    .deepSeekApi(deepSeekApi)
                    .defaultOptions(DeepSeekChatOptions.builder().model(modelCode).build())
                    .build();
            default -> {
                log.warn("不支持的厂商类型: {}", provider.getCode());
                yield null;
            }
        };
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

- [ ] **Step 4: Delete old config files**

删除 `aicoder-chat/src/main/java/com/ai/coder/chat/config/ChatModelConfig.java`
删除 `aicoder-chat/src/main/java/com/ai/coder/chat/service/ModelService.java`

- [ ] **Step 5: Modify ChatService**

在 `ChatService.java` 中：
- 将 `private final Map<String, ChatModel> modelMap` 改为 `private final DynamicModelRegistry modelRegistry`
- 将 `resolveModel` 方法改为：
```java
public ChatModel resolveModel(String modelId) {
    return modelRegistry.getChatModel(modelId);
}
```
- `buildPrompt` 方法中 `if (chatModel instanceof OllamaChatModel)` 的 OllamaChatOptions 设置逻辑不变，因为 DynamicModelRegistry 创建的 OllamaChatModel 已经带上了正确的 modelCode

- [ ] **Step 6: Modify AgentService**

在 `AgentService.java` 中：
- 将 `private final Map<String, ChatModel> modelMap` 改为 `private final DynamicModelRegistry modelRegistry`
- `executeAgentLoop` 中的 `ChatModel chatModel = modelMap.get(request.getModel())` 改为 `ChatModel chatModel = modelRegistry.getChatModel(request.getModel())`
- 将 `if (chatModel == null) chatModel = modelMap.values().iterator().next()` 改为 `if (chatModel == null) chatModel = modelRegistry.getChatModelMap().values().iterator().next()`

- [ ] **Step 7: Modify ModelController**

将 `ModelController.java` 改为：
```java
package com.ai.coder.chat.controller;

import com.ai.coder.chat.dto.ModelInfoDTO;
import com.ai.coder.chat.registry.DynamicModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ModelController {

    private final DynamicModelRegistry modelRegistry;

    @GetMapping("/models")
    public List<ModelInfoDTO> models() {
        return modelRegistry.getAvailableChatModels();
    }
}
```

- [ ] **Step 8: Modify application.yml**

将 chat 模块的 `application.yml` 中的 `spring.ai` 部分改为：
```yaml
  ai:
    ollama:
      base-url: http://localhost:11434
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
```
移除所有 `chat.options` 和 `embedding.options` 配置。

- [ ] **Step 9: Compile**

Run: `mvn compile -pl aicoder-chat -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add -A aicoder-chat/
git commit -m "feat(chat): DynamicModelRegistry 替代硬编码模型配置，从数据库动态加载模型"
```

---

## Task 5: Rag 模块 — DynamicModelRegistry + 重构

**Files:**
- Create: `aicoder-rag/src/main/java/com/ai/coder/rag/entity/ModelProvider.java`
- Create: `aicoder-rag/src/main/java/com/ai/coder/rag/entity/ModelConfig.java`
- Create: `aicoder-rag/src/main/java/com/ai/coder/rag/repository/ModelProviderRepository.java`
- Create: `aicoder-rag/src/main/java/com/ai/coder/rag/repository/ModelConfigRepository.java`
- Create: `aicoder-rag/src/main/java/com/ai/coder/rag/registry/DynamicModelRegistry.java`
- Delete: `aicoder-rag/src/main/java/com/ai/coder/rag/config/ChatModelConfig.java`
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/service/RagChatService.java`
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/service/SqlGenerationService.java`
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/config/DynamicVectorStoreConfig.java`
- Modify: `aicoder-rag/src/main/resources/application.yml`

- [ ] **Step 1: Create read-only entities and repositories**

与 Task 4 相同模式，package 改为 `com.ai.coder.rag.entity` 和 `com.ai.coder.rag.repository`。ModelProviderRepository 和 ModelConfigRepository 同 Task 4 Step 2。

- [ ] **Step 2: Create DynamicModelRegistry**

与 Task 4 Step 3 基本相同，但额外增加 EmbeddingModel 支持。在 `@PostConstruct init()` 中增加 EMBEDDING 类型模型的处理：

```java
private EmbeddingModel embeddingModel;

// 在 init() 中增加：
for (ModelConfig model : models) {
    if ("EMBEDDING".equals(model.getModelType())) {
        ModelProvider provider = providerMap.get(model.getProviderId());
        if (provider != null && "OLLAMA".equals(provider.getCode())) {
            embeddingModel = new OllamaEmbeddingModel(ollamaApi,
                    OllamaEmbeddingOptions.builder().model(model.getModelCode()).build());
            log.info("注册 Embedding 模型: {} ({})", model.getDisplayName(), model.getModelCode());
            break;
        }
    }
}
```

增加 import：`org.springframework.ai.embedding.EmbeddingModel`、`org.springframework.ai.ollama.OllamaEmbeddingModel`、`org.springframework.ai.ollama.api.OllamaEmbeddingOptions`

增加方法：
```java
public EmbeddingModel getEmbeddingModel() {
    if (embeddingModel == null) {
        throw new IllegalStateException("没有可用的 Embedding 模型，请在管理页面配置");
    }
    return embeddingModel;
}
```

- [ ] **Step 3: Delete ChatModelConfig**

删除 `aicoder-rag/src/main/java/com/ai/coder/rag/config/ChatModelConfig.java`

- [ ] **Step 4: Modify RagChatService**

将三个独立的 ChatModel 注入改为 DynamicModelRegistry：
```java
// 删除这三个字段：
// private final ChatModel defaultChatModel;
// private final ChatModel ollamaChatModel;
// private final ChatModel deepSeekChatModel;

// 改为：
private final DynamicModelRegistry modelRegistry;
```

将 `resolveChatModel` 方法改为：
```java
private ChatModel resolveChatModel(String model) {
    if (model == null || model.isBlank()) {
        return modelRegistry.getChatModelMap().values().iterator().next();
    }
    try {
        return modelRegistry.getChatModel(model);
    } catch (IllegalArgumentException e) {
        return modelRegistry.getChatModelMap().values().iterator().next();
    }
}
```

- [ ] **Step 5: Modify SqlGenerationService**

与 RagChatService 相同的模式：删除三个 ChatModel 注入，改为注入 `DynamicModelRegistry`，更新 `resolveChatModel` 方法。

- [ ] **Step 6: Modify DynamicVectorStoreConfig**

将 `private final EmbeddingModel embeddingModel` 改为 `private final DynamicModelRegistry modelRegistry`。在 `getVectorStore()` 方法中，所有使用 `embeddingModel` 的地方改为 `modelRegistry.getEmbeddingModel()`。

- [ ] **Step 7: Modify application.yml**

```yaml
  ai:
    ollama:
      base-url: http://localhost:11434
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
```
移除所有 `chat.options` 和 `embedding.options` 配置。

- [ ] **Step 8: Compile**

Run: `mvn compile -pl aicoder-rag -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add -A aicoder-rag/
git commit -m "feat(rag): DynamicModelRegistry 替代硬编码模型配置，Embedding 从数据库加载"
```

---

## Task 6: Workflow 模块 — DynamicModelRegistry + 重构

**Files:**
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/model/entity/ModelProvider.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/model/entity/ModelConfig.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/repository/ModelProviderRepository.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/repository/ModelConfigRepository.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/registry/DynamicModelRegistry.java`
- Delete: `aicoder-workflow/src/main/java/com/ai/coder/workflow/config/WorkflowModelConfig.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowExecutionService.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/config/DynamicVectorStoreConfig.java`
- Modify: `aicoder-workflow/src/main/resources/application.yml`

- [ ] **Step 1: Create entities, repositories, and DynamicModelRegistry**

与 Task 5 完全相同模式。package 为 `com.ai.coder.workflow.model.entity`、`com.ai.coder.workflow.repository`、`com.ai.coder.workflow.registry`。

- [ ] **Step 2: Delete WorkflowModelConfig**

删除 `aicoder-workflow/src/main/java/com/ai/coder/workflow/config/WorkflowModelConfig.java`

- [ ] **Step 3: Modify WorkflowExecutionService**

将 `private final Map<String, ChatModel> modelMap` 改为 `private final DynamicModelRegistry modelRegistry`。
将 `modelMap.values().stream().findFirst()` 改为 `modelRegistry.getChatModelMap().values().stream().findFirst()`。

- [ ] **Step 4: Modify WorkflowNodeFactory**

将构造函数参数 `Map<String, ChatModel> modelMap` 改为 `DynamicModelRegistry modelRegistry`。
将 `modelMap.get(model)` 改为 `modelRegistry.getChatModel(model)`。
将 `modelMap` 字段赋值改为 `modelRegistry`。

- [ ] **Step 5: Modify DynamicVectorStoreConfig**

与 Task 5 Step 6 相同：将 `EmbeddingModel embeddingModel` 改为 `DynamicModelRegistry modelRegistry`，所有使用处改为 `modelRegistry.getEmbeddingModel()`。

- [ ] **Step 6: Modify application.yml**

```yaml
  ai:
    ollama:
      base-url: http://localhost:11434
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
```

- [ ] **Step 7: Compile**

Run: `mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add -A aicoder-workflow/
git commit -m "feat(workflow): DynamicModelRegistry 替代硬编码模型配置"
```

---

## Task 7: System — 菜单结构调整

**Files:**
- Modify: `aicoder-system/src/main/java/com/ai/coder/system/init/DataInitializer.java`

- [ ] **Step 1: Update DataInitializer**

修改 `initMenus()` 方法：

移除原来的 `Menu vectordb = saveMenu(null, "向量库配置", "/vectordb", "VectorDb", 5, 2);`

在工作流之后新增模型管理目录：
```java
Menu modelMgmt = saveMenu(null, "模型管理", "/model", "ModelMgmt", 5, 1);
```

调整后续菜单的 sort：`profile` sort=6，`system` sort=7。

在系统管理子菜单前新增模型管理子菜单：
```java
Menu providerConfig = saveMenu(modelMgmt.getId(), "厂商配置", "/model/provider", "Provider", 1, 2);
Menu modelConfig = saveMenu(modelMgmt.getId(), "模型配置", "/model/config", "ModelConfig", 2, 2);
Menu vectordbConfig = saveMenu(modelMgmt.getId(), "向量库配置", "/model/vectordb", "VectorDb", 3, 2);
```

更新 `allMenus` 列表：
```java
List<Menu> allMenus = List.of(home, chat, knowledgeMgmt, knowledge, rag, sql,
        workflow, modelMgmt, providerConfig, modelConfig, vectordbConfig,
        profile, system, userMgmt, roleMgmt, permMgmt, menuMgmt);
```

- [ ] **Step 2: Clear old menu data and reinitialize**

```bash
docker exec mysql mysql -uroot -proot test_ai -e "DELETE FROM ai_role_menu; DELETE FROM ai_menu;"
```

- [ ] **Step 3: Compile and restart system service**

Run: `mvn compile -pl aicoder-system -q`

- [ ] **Step 4: Commit**

```bash
git add aicoder-system/src/main/java/com/ai/coder/system/init/DataInitializer.java
git commit -m "feat(system): 新增模型管理菜单（厂商配置/模型配置/向量库配置）"
```

---

## Task 8: 前端 — Types + API + 路由 + 页面

**Files:**
- Modify: `aicoder-web/src/types/index.ts`
- Create: `aicoder-web/src/api/model.ts`
- Modify: `aicoder-web/src/router/index.ts`
- Create: `aicoder-web/src/views/model/ProviderConfigView.vue`
- Create: `aicoder-web/src/views/model/ModelConfigView.vue`
- Move: `aicoder-web/src/views/admin/VectorDbConfigView.vue` → `aicoder-web/src/views/model/VectorDbConfigView.vue`

- [ ] **Step 1: Add types to index.ts**

在 `aicoder-web/src/types/index.ts` 末尾追加：
```ts
// ===== 模型管理 =====

export interface ModelProviderDTO {
  id: number
  name: string
  logo: string
  code: string
  baseUrl: string
  apiKey: string
  description: string
  enabled: number
  sort: number
}

export interface CreateProviderRequest {
  name: string
  logo?: string
  code: string
  baseUrl: string
  apiKey?: string
  description?: string
  enabled?: number
  sort?: number
}

export interface ModelConfigDTO {
  id: number
  providerId: number
  providerName: string
  displayName: string
  modelCode: string
  modelType: string
  enabled: number
  sort: number
}

export interface CreateModelConfigRequest {
  providerId: number
  displayName: string
  modelCode: string
  modelType: string
  enabled?: number
  sort?: number
}
```

- [ ] **Step 2: Create model API**

`aicoder-web/src/api/model.ts`:
```ts
import request from './request'
import type { ModelProviderDTO, CreateProviderRequest, ModelConfigDTO, CreateModelConfigRequest } from '@/types'

export const modelApi = {
  providerList: () => request.get<ModelProviderDTO[]>('/admin/model/provider/list'),
  providerGet: (id: number) => request.get<ModelProviderDTO>(`/admin/model/provider/${id}`),
  providerCreate: (data: CreateProviderRequest) => request.post<ModelProviderDTO>('/admin/model/provider', data),
  providerUpdate: (id: number, data: CreateProviderRequest) => request.put<ModelProviderDTO>(`/admin/model/provider/${id}`, data),
  providerDelete: (id: number) => request.delete(`/admin/model/provider/${id}`),

  configList: (modelType?: string) => request.get<ModelConfigDTO[]>('/admin/model/config/list', { params: { modelType } }),
  configGet: (id: number) => request.get<ModelConfigDTO>(`/admin/model/config/${id}`),
  configCreate: (data: CreateModelConfigRequest) => request.post<ModelConfigDTO>('/admin/model/config', data),
  configUpdate: (id: number, data: CreateModelConfigRequest) => request.put<ModelConfigDTO>(`/admin/model/config/${id}`, data),
  configDelete: (id: number) => request.delete(`/admin/model/config/${id}`)
}
```

- [ ] **Step 3: Update router**

在 `aicoder-web/src/router/index.ts` 的 children 数组中：
- 移除 `{ path: 'vectordb', name: 'VectorDb', component: () => import('@/views/admin/VectorDbConfigView.vue') }`
- 新增三条路由：
```ts
{ path: 'model/provider', name: 'ModelProvider', component: () => import('@/views/model/ProviderConfigView.vue') },
{ path: 'model/config', name: 'ModelConfig', component: () => import('@/views/model/ModelConfigView.vue') },
{ path: 'model/vectordb', name: 'VectorDb', component: () => import('@/views/model/VectorDbConfigView.vue') },
```

- [ ] **Step 4: Move VectorDbConfigView**

将 `src/views/admin/VectorDbConfigView.vue` 移动到 `src/views/model/VectorDbConfigView.vue`（文件内容不变）。

- [ ] **Step 5: Create ProviderConfigView**

`aicoder-web/src/views/model/ProviderConfigView.vue` — 参考现有 `VectorDbConfigView.vue` 的样式，实现：
- 表格展示：厂商名称、商标、编码、Base URL、API Key（脱敏显示）、状态（启用/禁用标签）、操作（编辑/删除）
- 新增/编辑弹窗：name(必填), logo, code(新增时可编辑), baseUrl(默认 `http://localhost:11434`), apiKey, description, enabled(默认启用), sort
- 删除前检查有关联模型的提示

- [ ] **Step 6: Create ModelConfigView**

`aicoder-web/src/views/model/ModelConfigView.vue` — 参考现有 `VectorDbConfigView.vue` 的样式，实现：
- 表格展示：模型名称、模型编码、所属厂商、模型类型（CHAT/EMBEDDING 标签）、状态、操作
- 新增/编辑弹窗：providerId(下拉选择厂商列表), displayName, modelCode, modelType(下拉 CHAT/EMBEDDING), enabled, sort
- 页面加载时同时获取厂商列表（用于下拉）和模型列表

- [ ] **Step 7: Commit**

```bash
git add -A aicoder-web/src/
git commit -m "feat(web): 新增厂商配置/模型配置页面，迁移向量库配置到模型管理目录"
```

---

## Task 9: 编译验证 + 全链路测试

- [ ] **Step 1: 全量编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 重启所有服务并测试**

启动 admin、system、chat、rag、workflow、gateway，验证：
1. 厂商配置 CRUD API
2. 模型配置 CRUD API
3. Chat 模型列表 API 是否从数据库返回
4. 前端模型管理三个页面正常渲染
5. 对话功能正常使用动态加载的模型

- [ ] **Step 3: Commit all and tag**

```bash
git add -A
git commit -m "feat: 模型管理与动态模型加载完整实现"
git tag v1.1.0
```
