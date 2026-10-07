# 模型注册表热重载 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** admin 改模型配置（create/update[含启停]/delete）后自动通知 chat/rag/workflow 重载内存注册表，启用即生效、免重启。

**Architecture:** aicoder-core 基类把 init 构建逻辑抽成 `synchronized reload()`（volatile 原子换 map + 重跑探针 + registerExtra）；core 新增一份 `RegistryReloadController`（`/internal/registry/reload`），三 app 经 `@ComponentScan("com.ai.coder.core")` 复用；admin 补 loadbalancer starter + `@EnableAsync`，新增 `RegistryReloadNotifier`（@Async fire-and-forget，对三服务各发 reload），在 ModelConfigController 写操作后触发。

**Tech Stack:** Spring Boot 3.5 / Spring Cloud（@LoadBalanced RestTemplate + Nacos LB）/ Spring AI 1.1.2 / JPA。

**Build/test:** `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`（common/core 改动后需 `install -DskipTests` 到 .m2 供 spring-boot:run 解析）。

**Spec:** [2026-06-24-model-registry-hot-reload-design.md](../specs/2026-06-24-model-registry-hot-reload-design.md)

---

## File Structure

**aicoder-core**
- Modify `registry/AbstractDynamicModelRegistry.java`（抽 reload + chatModels 改 volatile）
- Create `web/RegistryReloadController.java`（共享 reload 端点）
- Test: modify `registry/AbstractDynamicModelRegistryTest.java`（加 reload 用例）

**chat / rag / workflow**（同构）
- Modify 各 app 类（加 `@ComponentScan` 含 core 包）

**aicoder-admin**
- Modify `pom.xml`（加 loadbalancer starter）
- Modify `AicoderAdminApplication.java`（加 `@EnableAsync`）
- Create `service/RegistryReloadNotifier.java`（@Async reloadAll）
- Test: `service/RegistryReloadNotifierTest.java`
- Modify `controller/ModelConfigController.java`（写操作后触发 notifier）

---

## Task 1: 基类抽 reload() + chatModels 改 volatile（core，TDD）

**Files:**
- Modify: `aicoder-core/src/main/java/com/ai/coder/core/registry/AbstractDynamicModelRegistry.java`
- Test: `aicoder-core/src/test/java/com/ai/coder/core/registry/AbstractDynamicModelRegistryTest.java`

- [ ] **Step 1: 加 reload 失败测试**

在 `AbstractDynamicModelRegistryTest` 末尾（class 闭合 `}` 前）加：
```java
    @Test
    void reload_rebuilds_registry_with_new_model_set() {
        ModelProvider deepseek = new ModelProvider();
        deepseek.setId(1L);
        deepseek.setCode("DEEPSEEK");
        deepseek.setApiKey("sk-test");
        deepseek.setBaseUrl("https://api.deepseek.com");

        ModelConfig first = new ModelConfig();
        first.setProviderId(1L); first.setModelCode("deepseek-a"); first.setDisplayName("A"); first.setModelType("CHAT");
        ModelConfig second = new ModelConfig();
        second.setProviderId(1L); second.setModelCode("deepseek-b"); second.setDisplayName("B"); second.setModelType("CHAT");

        ModelConfigRepository modelConfigRepo = mock(ModelConfigRepository.class);
        ModelProviderRepository providerRepo = mock(ModelProviderRepository.class);
        when(providerRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(deepseek));
        when(modelConfigRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(first))
                .thenReturn(List.of(second)); // 第一次 init 用 first，第二次 reload 用 second

        AbstractDynamicModelRegistry registry = new AbstractDynamicModelRegistry(modelConfigRepo, providerRepo) {};
        registry.init();
        assertNotNull(registry.getChatModel("deepseek-a"), "init 注册 deepseek-a");

        registry.reload();
        assertNotNull(registry.getChatModel("deepseek-b"), "reload 后新模型 deepseek-b 可用");
        assertThrows(IllegalArgumentException.class, () -> registry.getChatModel("deepseek-a"),
                "reload 后旧模型 deepseek-a 应已被新表替换掉");
    }
```

- [ ] **Step 2: 运行测试确认失败**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core test -Dtest=AbstractDynamicModelRegistryTest
```
Expected: 编译失败（`reload()` 未定义）。

- [ ] **Step 3: 抽 reload() + chatModels 改 volatile**

在 `AbstractDynamicModelRegistry.java`：
(a) `chatModels` 字段去掉 `final`、加 `volatile`：
```java
    protected volatile Map<String, ChatModel> chatModels = new HashMap<>();
```
(b) 把 `init()` 的构建逻辑整体移入新方法 `reload()`，`init()` 改为只调 `reload()`。即把现有 `@PostConstruct public void init() { ... 全部构建逻辑 ... }` 替换为：
```java
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重载：重读 DB → 构建局部新表 → 原子替换 chatModels（volatile）→ 重跑探针 + registerExtra。
     * synchronized 串行化防并发 reload 互相覆盖；getChatModel 读 volatile 引用，要么旧表要么新表。
     */
    public synchronized void reload() {
        List<ModelProvider> providers = providerRepository.findByEnabledOrderBySortAsc(1);
        List<ModelConfig> models = modelConfigRepository.findByEnabledOrderBySortAsc(1);

        Map<Long, ModelProvider> providerMap = new HashMap<>();
        for (ModelProvider p : providers) {
            providerMap.put(p.getId(), p);
        }

        Map<String, ChatModel> built = new HashMap<>();
        for (ModelConfig model : models) {
            if (!"CHAT".equals(model.getModelType())) continue;
            ModelProvider provider = providerMap.get(model.getProviderId());
            if (provider == null) continue;

            ChatModel chatModel = createChatModel(provider, model.getModelCode());
            if (chatModel != null) {
                built.put(model.getModelCode(), chatModel);
                log.info("注册 Chat 模型: {} ({})", model.getDisplayName(), model.getModelCode());
                if ("OLLAMA".equals(provider.getCode())) {
                    probeOllama(provider.getBaseUrl(), model.getDisplayName(), model.getModelCode());
                }
            }
        }
        registerExtra(models, providerMap);
        // 原子替换：并发 getChatModel 读到旧表或新表，不会半破
        this.chatModels = built;
        log.info("DynamicModelRegistry 初始化完成，共 {} 个 Chat 模型", chatModels.size());
    }
```
注意：`registerExtra` 子类钩子（rag/workflow 建 embedding）在原子替换前调用——子类若把 embedding 引用存在自己字段（非 chatModels），不受影响；钩子里不应读 chatModels（它读 models/providerMap 参数）。`getChatModelMap()` 返回 `Collections.unmodifiableMap(chatModels)`——reload 换引用后，旧调用方持有的旧 unmodifiable 视图仍是旧表（安全，下次调用取新表）。

- [ ] **Step 4: 运行测试确认通过**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-core test -Dtest=AbstractDynamicModelRegistryTest
```
Expected: `Tests run: 2, Failures: 0`（原 init 用例 + 新 reload 用例）。

- [ ] **Step 5: 提交**
```bash
git add aicoder-core/src/main/java/com/ai/coder/core/registry/AbstractDynamicModelRegistry.java aicoder-core/src/test/java/com/ai/coder/core/registry/AbstractDynamicModelRegistryTest.java
git commit -m "feat(core): AbstractDynamicModelRegistry 抽 reload() + chatModels volatile 原子换（热重载基础）"
```

---

## Task 2: core 共享 RegistryReloadController（TDD）

**Files:**
- Create: `aicoder-core/src/main/java/com/ai/coder/core/web/RegistryReloadController.java`
- Test: `aicoder-core/src/test/java/com/ai/coder/core/web/RegistryReloadControllerTest.java`

- [ ] **Step 1: 写失败测试**

Create `aicoder-core/src/test/java/com/ai/coder/core/web/RegistryReloadControllerTest.java`:
```java
package com.ai.coder.core.web;

import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistryReloadControllerTest {

    @Test
    void reload_delegates_to_registry() {
        AbstractDynamicModelRegistry registry = mock(AbstractDynamicModelRegistry.class);
        RegistryReloadController controller = new RegistryReloadController(registry);

        ResponseEntity<Map<String, String>> resp = controller.reload();

        verify(registry).reload();
        assertEquals(200, resp.getStatusCode().value());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core test -Dtest=RegistryReloadControllerTest
```
Expected: 编译失败（`RegistryReloadController` 不存在）。

- [ ] **Step 3: 实现 controller**

Create `aicoder-core/src/main/java/com/ai/coder/core/web/RegistryReloadController.java`:
```java
package com.ai.coder.core.web;

import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模型注册表热重载端点（内部，不经 Gateway）。
 * 一份 controller 服务 chat/rag/workflow——各 app 经 @ComponentScan("com.ai.coder.core") 扫描到此 bean。
 * 由 admin 在模型配置变更后通过 @LoadBalanced RestTemplate 调用，实现"启用即生效"。
 */
@RestController
@RequestMapping("/internal/registry")
@RequiredArgsConstructor
public class RegistryReloadController {

    private final AbstractDynamicModelRegistry registry;

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        registry.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-core test -Dtest=RegistryReloadControllerTest
```
Expected: `Tests run: 1, Failures: 0`。

- [ ] **Step 5: 提交**
```bash
git add aicoder-core/src/main/java/com/ai/coder/core/web/RegistryReloadController.java aicoder-core/src/test/java/com/ai/coder/core/web/RegistryReloadControllerTest.java
git commit -m "feat(core): RegistryReloadController 共享热重载端点（/internal/registry/reload）"
```

---

## Task 3: chat/rag/workflow 加 @ComponentScan 复用 reload controller

**Files:**
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java`
- Modify: `aicoder-rag/src/main/java/com/ai/coder/rag/AicoderRagApplication.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/AicoderWorkflowApplication.java`

> 无单测（注解接线）；验证=三模块编译 + 重启后 `/internal/registry/reload` 可调。

- [ ] **Step 1: chat app 加 @ComponentScan**

在 `aicoder-chat/.../AicoderChatApplication.java` 加 import：
```java
import org.springframework.context.annotation.ComponentScan;
```
并在注解块加 `@ComponentScan`（与现有 `@SpringBootApplication`/`@EntityScan`/`@EnableJpaRepositories` 并列）：
```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
@ComponentScan(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
```
（读文件确认现有注解顺序，保持其余不变。）

- [ ] **Step 2: rag app 加 @ComponentScan**

在 `aicoder-rag/.../AicoderRagApplication.java` 加 import + 注解：
```java
import org.springframework.context.annotation.ComponentScan;
```
```java
@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
@ComponentScan(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
```

- [ ] **Step 3: workflow app 加 @ComponentScan**

在 `aicoder-workflow/.../AicoderWorkflowApplication.java` 加 import + 注解：
```java
import org.springframework.context.annotation.ComponentScan;
```
```java
@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
@ComponentScan(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
```

- [ ] **Step 4: install core + 编译三模块**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-core install -DskipTests
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-chat,aicoder-rag,aicoder-workflow -am compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 5: 重启 chat 验证 reload 端点生效**
```bash
pkill -9 -f AicoderChatApplication 2>/dev/null; sleep 3
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home nohup /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn spring-boot:run -pl aicoder-chat > /tmp/svc-chat.log 2>&1 &
sleep 60
curl -s -X POST http://localhost:8082/internal/registry/reload | head -c 100; echo
grep -E "DynamicModelRegistry 初始化完成" /tmp/svc-chat.log | tail -1
```
Expected: 返回 `{"status":"reloaded"}`；日志多一条"初始化完成"（reload 重跑）。停：`pkill -9 -f AicoderChatApplication 2>/dev/null`。

- [ ] **Step 6: 提交**
```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java aicoder-rag/src/main/java/com/ai/coder/rag/AicoderRagApplication.java aicoder-workflow/src/main/java/com/ai/coder/workflow/AicoderWorkflowApplication.java
git commit -m "feat(chat,rag,workflow): @ComponentScan com.ai.coder.core 复用 RegistryReloadController"
```

---

## Task 4: admin 补 loadbalancer + @EnableAsync + RegistryReloadNotifier（TDD）

**Files:**
- Modify: `aicoder-admin/pom.xml`
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/AicoderAdminApplication.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/service/RegistryReloadNotifier.java`
- Test: `aicoder-admin/src/test/java/com/ai/coder/admin/service/RegistryReloadNotifierTest.java`

- [ ] **Step 1: admin pom 加 loadbalancer starter**

在 `aicoder-admin/pom.xml` `<dependencies>` 内（紧邻 nacos-discovery 依赖后）加：
```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
```
（版本由 spring-cloud-bom 管理；与 chat 同款，让 @LoadBalanced RestTemplate 解析服务名。）

- [ ] **Step 2: admin app 加 @EnableAsync**

在 `aicoder-admin/.../AicoderAdminApplication.java` 加 import + 注解（与 `@SpringBootApplication` 并列）：
```java
import org.springframework.scheduling.annotation.EnableAsync;
```
```java
@SpringBootApplication
@EnableAsync
public class AicoderAdminApplication {
```

- [ ] **Step 3: 写 RegistryReloadNotifier 失败测试**

Create `aicoder-admin/src/test/java/com/ai/coder/admin/service/RegistryReloadNotifierTest.java`:
```java
package com.ai.coder.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RegistryReloadNotifierTest {

    private RestTemplate restTemplate;
    private RegistryReloadNotifier notifier;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        notifier = new RegistryReloadNotifier(restTemplate);
    }

    @Test
    void reloadAll_posts_reload_to_chat_rag_workflow() {
        notifier.reloadAll();

        verify(restTemplate).postForObject(eq("http://aicoder-chat/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-rag/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-workflow/internal/registry/reload"), eq(null), eq(Object.class));
    }

    @Test
    void reloadAll_continues_when_one_service_fails() {
        // chat 抛异常 → 不传播，其余继续
        when(restTemplate.postForObject(eq("http://aicoder-chat/internal/registry/reload"), eq(null), eq(Object.class)))
                .thenThrow(new RuntimeException("chat 不可达"));

        notifier.reloadAll(); // 不应抛

        verify(restTemplate).postForObject(eq("http://aicoder-chat/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-rag/internal/registry/reload"), eq(null), eq(Object.class));
        verify(restTemplate).postForObject(eq("http://aicoder-workflow/internal/registry/reload"), eq(null), eq(Object.class));
    }
}
```

- [ ] **Step 4: 运行测试确认失败**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-admin test -Dtest=RegistryReloadNotifierTest
```
Expected: 编译失败（`RegistryReloadNotifier` 不存在）。

- [ ] **Step 5: 实现 RegistryReloadNotifier**

Create `aicoder-admin/src/main/java/com/ai/coder/admin/service/RegistryReloadNotifier.java`:
```java
package com.ai.coder.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 模型配置变更后通知 chat/rag/workflow 热重载注册表（@Async fire-and-forget）。
 * 每服务独立 try/catch：某服务挂/失败只记日志，不影响 admin 保存与其它服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryReloadNotifier {

    private static final List<String> SERVICES = List.of("aicoder-chat", "aicoder-rag", "aicoder-workflow");

    private final RestTemplate restTemplate;

    @Async
    public void reloadAll() {
        for (String service : SERVICES) {
            try {
                restTemplate.postForObject(
                        "http://" + service + "/internal/registry/reload", null, Object.class);
                log.info("已通知 {} 重载模型注册表", service);
            } catch (Exception e) {
                log.warn("通知 {} 重载模型注册表失败（不影响保存，下次重启补偿）：{}", service, e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 6: 运行测试确认通过**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-admin test -Dtest=RegistryReloadNotifierTest
```
Expected: `Tests run: 2, Failures: 0`。

- [ ] **Step 7: 提交**
```bash
git add aicoder-admin/pom.xml aicoder-admin/src/main/java/com/ai/coder/admin/AicoderAdminApplication.java aicoder-admin/src/main/java/com/ai/coder/admin/service/RegistryReloadNotifier.java aicoder-admin/src/test/java/com/ai/coder/admin/service/RegistryReloadNotifierTest.java
git commit -m "feat(admin): RegistryReloadNotifier @Async 通知三服务重载 + loadbalancer starter + @EnableAsync"
```

---

## Task 5: admin ModelConfigController 写操作后触发 notifier

**Files:**
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelConfigController.java`

> 无单测（controller 接线，3 行调用）；验证=编译 + Task 6 端到端。

- [ ] **Step 1: ModelConfigController 注入 notifier + 三处写操作后触发**

先读文件确认现有 import + 字段（当前注入 `ModelConfigService modelConfigService`）。加 import：
```java
import com.ai.coder.admin.service.RegistryReloadNotifier;
```
加字段（与 `modelConfigService` 并列，`@RequiredArgsConstructor` 自动注入）：
```java
    private final RegistryReloadNotifier registryReloadNotifier;
```
把 create/update/delete 三个方法改为保存后触发 reload：
```java
    @PostMapping
    public ResponseEntity<ModelConfigDTO> create(@RequestBody CreateModelConfigRequest request) {
        ModelConfigDTO result = modelConfigService.create(request);
        registryReloadNotifier.reloadAll();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfigDTO> update(@PathVariable Long id, @RequestBody CreateModelConfigRequest request) {
        ModelConfigDTO result = modelConfigService.update(id, request);
        registryReloadNotifier.reloadAll();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        registryReloadNotifier.reloadAll();
        return ResponseEntity.ok().build();
    }
```
（`reloadAll()` 是 @Async，调用立即返回，不阻塞响应。）

- [ ] **Step 2: 编译验证**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -q -pl aicoder-admin -am compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add aicoder-admin/src/main/java/com/ai/coder/admin/controller/ModelConfigController.java
git commit -m "feat(admin): ModelConfigController 写操作(create/update/delete)后触发注册表热重载"
```

---

## Task 6: 端到端验证（启用模型即时生效，免重启）

**Files:** 无代码改动——验证清单。需中间件 + admin + chat（+ rag/workflow）运行。

- [ ] **Step 1: 全模块编译 + 测试**
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn clean compile -DskipTests
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn -pl aicoder-core,aicoder-admin test
```
Expected: 全绿（含 reload + controller + notifier 新单测）。

- [ ] **Step 2: 启动 admin + chat（+ rag/workflow 可选）+ 中间件**

确保 MySQL/Redis/Nacos 起来。install core 到 .m2（`mvn -pl aicoder-core install -DskipTests -q`）。启动 aicoder-admin（:8081）与 aicoder-chat（:8082）。确认 chat 启动日志注册了初始模型集。

- [ ] **Step 3: 端到端——admin 新增/启用模型，chat 不重启即可用**

经 Gateway 用 admin 登录拿 token。先确认 chat 当前不认某未注册模型：
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
# 用 admin 接口启用一个之前未启用的模型（或新增一个 Ollama 模型，如 glm-4.7-flash:latest）
curl -s -X POST http://localhost:8080/api/admin/model/config -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"providerId":1,"modelCode":"glm-4.7-flash:latest","displayName":"GLM 4.7 Flash","modelType":"CHAT","enabled":1,"sort":50}' | head -c 200
```
观察 admin 日志：应有"已通知 aicoder-chat 重载模型注册表"。chat 日志：应有新的"注册 Chat 模型: GLM 4.7 Flash (glm-4.7-flash:latest)" + "初始化完成"（reload 重跑）。

- [ ] **Step 4: chat 不重启即可用新模型**

用 chat 接口发一条用新模型的对话（经 Gateway + token）：
```bash
curl -s -X POST http://localhost:8080/api/chat/send -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"model":"glm-4.7-flash:latest","message":"hi","conversationId":null}' --max-time 90 | head -c 300
```
Expected: 正常回复（不再 `IllegalArgumentException: 不支持的模型`）。证明热重载生效、免重启。

- [ ] **Step 5: 失败补偿验证（fire-and-forget）**

停掉 chat，再经 admin 改一次模型配置 → admin 保存仍成功返回（不报错），admin 日志记"通知 aicoder-chat 重载失败"；再启动 chat → chat 启动时 @PostConstruct 读到最新 DB（自然补偿）。

- [ ] **Step 6: 记录结论 + 清理**

把端到端结论记录。停服务。

---

## 完成后

使用 superpowers:finishing-a-development-branch 收尾（全模块编译+测试绿 → 呈现选项）。本轮提交默认留本地 main、不推送。
