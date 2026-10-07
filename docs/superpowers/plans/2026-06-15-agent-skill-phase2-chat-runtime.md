# Agent Skill Phase 2 — chat 运行面读侧接入实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `aicoder-chat` (:8082) 普通对话路径消费共享技能目录——注入 ACTIVE 技能目录到系统提示，模型按需 `read_skill` 加载完整 SKILL.md。

**Architecture:** chat 引入 Spring AI `ChatClient`（包裹既有 `ChatModel`），挂 native `SkillPromptAugmentAdvisor`（注入目录）+ 自建 `ReadSkillTool`（读 SKILL.md）；技能目录复用 Nacos `aicoder-shared.yml` 的 `skill.directory`（Phase 1 已验证）。`AgentService`（手搓 ReAct）不动。

**Tech Stack:** Spring AI 1.1.2 / Spring AI Alibaba `spring-ai-alibaba-graph-core` 1.1.2.2 / Spring Cloud 2025 + Nacos config / JUnit 5 + Mockito。

**关联设计：** [docs/superpowers/specs/2026-06-15-agent-skill-phase2-chat-runtime-design.md](../specs/2026-06-15-agent-skill-phase2-chat-runtime-design.md)

**范围边界（本计划不做）：** `submit_skill_draft` 写回与自进化闭环（Phase 3）；`AgentService` 技能化与 3 工具迁移；rag/workflow 接入；目录热刷新（初版启动扫描）。

---

## File Structure

**Create:**
- `aicoder-chat/src/main/java/com/ai/coder/chat/config/SkillProperties.java` — `@ConfigurationProperties("skill")`，绝对路径解析 + 启动日志（复刻 aicoder-skill 同名类）
- `aicoder-chat/src/main/java/com/ai/coder/chat/config/SkillRuntimeConfig.java` — 构建 `FileSystemSkillRegistry` + `SkillPromptAugmentAdvisor` bean
- `aicoder-chat/src/main/java/com/ai/coder/chat/tool/ReadSkillTool.java` — `@Tool readSkill(name)`
- `aicoder-chat/src/test/java/com/ai/coder/chat/config/SkillPropertiesTest.java`
- `aicoder-chat/src/test/java/com/ai/coder/chat/config/SkillRuntimeConfigTest.java`
- `aicoder-chat/src/test/java/com/ai/coder/chat/tool/ReadSkillToolTest.java`

**Modify:**
- `aicoder-chat/pom.xml` — 加 `spring-ai-alibaba-graph-core@1.1.2.2` + `spring-cloud-starter-alibaba-nacos-config`
- `aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java` — `chat()`/`chatStream()` 改用 `ChatClient`（挂 advisor + read_skill）
- `aicoder-chat/src/main/resources/application.yml` — `spring.config.import` + `skill.directory`
- `aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java` — 加 `@ConfigurationPropertiesScan`（若尚无）

---

### Task 1: 依赖与配置扫描（pom + 启动类）

**Files:**
- Modify: `aicoder-chat/pom.xml`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java`

- [ ] **Step 1: pom 加 graph-core + nacos-config**

在 `aicoder-chat/pom.xml` 的 DeepSeek 依赖块（约 44-48 行）之后、`mysql-connector-j` 之前，新增：

```xml
        <!-- Spring AI Alibaba graph-core: SkillRegistry + SkillPromptAugmentAdvisor -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-ai-alibaba-graph-core</artifactId>
            <version>1.1.2.2</version>
        </dependency>
        <!-- Nacos config（读共享 skill.directory） -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
```

（graph-core 显式 1.1.2.2 同 workflow；nacos-config 版本由父 BOM 管理。）

- [ ] **Step 2: 确认启动类有 `@ConfigurationPropertiesScan`**

读 `aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java`。若无 `@ConfigurationPropertiesScan` 注解，添加之，使 `SkillProperties`（Task 2）被扫描。最终类注解应包含：

```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class AicoderChatApplication { ... }
```

（若已有则跳过。）

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn clean compile -pl aicoder-chat -DskipTests`
Expected: BUILD SUCCESS（graph-core 1.1.2.2 解析成功，与 workflow 一致）。

- [ ] **Step 4: Commit**

```bash
git add aicoder-chat/pom.xml aicoder-chat/src/main/java/com/ai/coder/chat/AicoderChatApplication.java
git commit -m "feat(chat): 加 graph-core 1.1.2.2 + nacos-config 依赖（技能读侧前置）"
```

---

### Task 2: SkillProperties（绝对路径解析 + 启动日志）

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/config/SkillProperties.java`
- Create: `aicoder-chat/src/test/java/com/ai/coder/chat/config/SkillPropertiesTest.java`

- [ ] **Step 1: 写失败测试 `SkillPropertiesTest.java`**

```java
package com.ai.coder.chat.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPropertiesTest {

    @Test
    void resolved_directory_is_absolute_for_relative_input() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("./skills");

        String resolved = props.getResolvedDirectory();

        assertTrue(new File(resolved).isAbsolute(), "解析后应为绝对路径");
        assertEquals("./skills", props.getDirectory(), "原始配置值不变");
    }

    @Test
    void resolved_directory_is_idempotent_for_absolute_input() {
        SkillProperties props = new SkillProperties();
        props.setDirectory("/tmp/some-where");

        assertEquals("/tmp/some-where", props.getResolvedDirectory());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat -Dtest=SkillPropertiesTest`
Expected: 编译失败（`SkillProperties` 不存在）。

- [ ] **Step 3: 创建 `SkillProperties.java`（与 aicoder-skill 同名类同构，无 common 模块故接受重复）**

```java
package com.ai.coder.chat.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.io.File;

@Slf4j
@RefreshScope
@Data
@ConfigurationProperties(prefix = "skill")
public class SkillProperties {

    /**
     * 技能共享目录（与 aicoder-skill 指向同一物理路径，经 Nacos aicoder-shared.yml 统一）。
     * 可为相对（相对 JVM 工作目录）或绝对；用 {@link #getResolvedDirectory()} 取绝对路径。
     */
    private String directory = "./skills";

    /** 把 directory 解析为绝对路径；相对路径相对 JVM 工作目录。绝对输入幂等。 */
    public String getResolvedDirectory() {
        File file = new File(directory);
        return file.isAbsolute() ? directory : file.getAbsolutePath();
    }

    @PostConstruct
    void logResolvedDirectory() {
        log.warn("[skill] directory: configured='{}' resolved='{}'", directory, getResolvedDirectory());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat -Dtest=SkillPropertiesTest`
Expected: 2 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/config/SkillProperties.java aicoder-chat/src/test/java/com/ai/coder/chat/config/SkillPropertiesTest.java
git commit -m "feat(chat): SkillProperties 绝对路径解析 + 启动日志（TDD）"
```

---

### Task 3: ReadSkillTool（TDD）

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/tool/ReadSkillTool.java`
- Create: `aicoder-chat/src/test/java/com/ai/coder/chat/tool/ReadSkillToolTest.java`

- [ ] **Step 1: 写失败测试 `ReadSkillToolTest.java`**

```java
package com.ai.coder.chat.tool;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.ai.coder.chat.config.SkillProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadSkillToolTest {

    @TempDir
    Path tempDir;

    private ReadSkillTool tool;

    @BeforeEach
    void setUp() throws Exception {
        // 准备一个合法技能目录 {tempDir}/greeting-skill/SKILL.md
        Path skillDir = tempDir.resolve("greeting-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: greeting-skill
                description: 问候
                ---
                # Greeting
                正文
                """);

        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(props.getResolvedDirectory())
                .build();
        tool = new ReadSkillTool(registry);
    }

    @Test
    void readSkill_returns_content_when_found() {
        String content = tool.readSkill("greeting-skill");
        assertTrue(content.contains("# Greeting"), "应返回 SKILL.md 正文");
    }

    @Test
    void readSkill_returns_friendly_hint_when_missing() {
        String content = tool.readSkill("does-not-exist");
        assertFalse(content.contains("# Greeting"));
        assertTrue(content.contains("does-not-exist"), "提示应包含请求的技能名");
    }

    @Test
    void readSkill_rejects_path_traversal_name() {
        assertThrows(IllegalArgumentException.class, () -> tool.readSkill("../etc/evil"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat -Dtest=ReadSkillToolTest`
Expected: 编译失败（`ReadSkillTool` 不存在）。

- [ ] **Step 3: 创建 `ReadSkillTool.java`**

```java
package com.ai.coder.chat.tool;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 模型可调用的 read_skill 工具：按名加载技能完整 SKILL.md 正文（渐进式披露）。
 * graph-core 1.1.2.2 无框架版 read_skill，此处为薄封装，委托 SkillRegistry.readSkillContent。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadSkillTool {

    private final SkillRegistry skillRegistry;

    @Tool(description = "读取指定技能的完整 SKILL.md 内容。当系统提示中的技能列表里某个技能适用于当前任务时调用。参数 skillName 为技能名（kebab-case）。")
    public String readSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("技能名不能为空");
        }
        if (skillName.contains("..") || skillName.contains("/") || skillName.contains("\\")
                || skillName.contains(":")) {
            throw new IllegalArgumentException("非法的技能名：" + skillName);
        }
        if (!skillRegistry.contains(skillName)) {
            return "未找到技能：" + skillName + "。可用技能见系统提示中的技能列表，或确认技能名拼写。";
        }
        try {
            return skillRegistry.readSkillContent(skillName);
        } catch (IOException e) {
            log.warn("读取技能 {} 失败", skillName, e);
            return "读取技能 " + skillName + " 失败，请稍后重试。";
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat -Dtest=ReadSkillToolTest`
Expected: 3 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/tool/ReadSkillTool.java aicoder-chat/src/test/java/com/ai/coder/chat/tool/ReadSkillToolTest.java
git commit -m "feat(chat): ReadSkillTool 模型工具，按需加载 SKILL.md（TDD）"
```

---

### Task 4: SkillRuntimeConfig（registry + advisor bean）

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/config/SkillRuntimeConfig.java`
- Create: `aicoder-chat/src/test/java/com/ai/coder/chat/config/SkillRuntimeConfigTest.java`

- [ ] **Step 1: 写失败测试 `SkillRuntimeConfigTest.java`**

```java
package com.ai.coder.chat.config;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void builds_registry_and_advisor_pointing_at_skill_directory() throws Exception {
        // 准备一个技能
        Path skillDir = tempDir.resolve("faq-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: faq-skill
                description: 常见问题
                ---
                # FAQ
                """);

        SkillProperties props = new SkillProperties();
        props.setDirectory(tempDir.toString());
        SkillRuntimeConfig config = new SkillRuntimeConfig(props);

        SkillRegistry registry = config.skillRegistry();
        assertNotNull(registry);
        assertEquals(1, registry.size(), "应扫描到 1 个技能");
        assertTrue(registry.contains("faq-skill"));

        SkillPromptAugmentAdvisor advisor = config.skillPromptAugmentAdvisor(registry);
        assertNotNull(advisor);
        assertEquals(1, advisor.getSkillCount());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat -Dtest=SkillRuntimeConfigTest`
Expected: 编译失败（`SkillRuntimeConfig` 不存在）。

- [ ] **Step 3: 创建 `SkillRuntimeConfig.java`**

```java
package com.ai.coder.chat.config;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 构建技能运行面单例：FileSystemSkillRegistry（扫描共享 skills/ 目录）+ SkillPromptAugmentAdvisor（注入目录到系统提示）。
 * 目录缺失时创建空目录使 registry 优雅降级为空（chat 仍可用，仅无技能目录注入）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SkillRuntimeConfig {

    private final SkillProperties skillProperties;

    @Bean
    public SkillRegistry skillRegistry() {
        String dir = skillProperties.getResolvedDirectory();
        try {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.warn("[skill] 共享技能目录不存在，已创建空目录：{}", dir);
            }
        } catch (Exception e) {
            log.warn("[skill] 创建技能目录失败 {}：{}", dir, e.getMessage());
        }
        return FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(dir)
                .build();
    }

    @Bean
    public SkillPromptAugmentAdvisor skillPromptAugmentAdvisor(SkillRegistry registry) {
        return SkillPromptAugmentAdvisor.builder()
                .skillRegistry(registry)
                .build();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat -Dtest=SkillRuntimeConfigTest`
Expected: 1 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/config/SkillRuntimeConfig.java aicoder-chat/src/test/java/com/ai/coder/chat/config/SkillRuntimeConfigTest.java
git commit -m "feat(chat): SkillRuntimeConfig 构建共享目录 registry + SkillPromptAugmentAdvisor（TDD）"
```

---

### Task 5: ChatService 接入 ChatClient（advisor + read_skill）

**Files:**
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java`

> 本任务改动普通对话主路径，单测难以覆盖（需真实 ChatModel）；以编译 + 既有测试不退化 + 端到端（Task 7）验证。

- [ ] **Step 1: 修改 `ChatService.java`——构造器注入 advisor + read_skill，chat()/chatStream() 改用 ChatClient**

在 import 区新增：

```java
import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.ai.coder.chat.tool.ReadSkillTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
```
（删除已不再使用的 `Prompt` import 若 ChatClient 路径不再用它；保留 AssistantMessage/UserMessage/SystemMessage/Message。）

构造器新增两个依赖（`@RequiredArgsConstructor` 自动生成，加 final 字段）。在类字段区（`private final ChatMessageRepository chatMessageRepository;` 之后）新增：

```java
    private final SkillPromptAugmentAdvisor skillAdvisor;
    private final ReadSkillTool readSkillTool;
```

新增私有方法（构建按请求的 ChatClient，复用单例 advisor/tool）：

```java
    private ChatClient buildSkillClient(String modelId) {
        ChatModel chatModel = resolveModel(modelId);
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(skillAdvisor)
                .defaultTools(readSkillTool);
        if (chatModel instanceof OllamaChatModel) {
            builder.defaultOptions(OllamaChatOptions.builder().model(modelId).build());
        }
        return builder.build();
    }
```

把 `chat()` 中：

```java
        Prompt prompt = buildPrompt(request.getModel(), request.getMessage(), conversationId);
        ChatModel chatModel = resolveModel(request.getModel());
        String responseContent = chatModel.call(prompt).getResult().getOutput().getText();
```

替换为：

```java
        String responseContent = buildSkillClient(request.getModel())
                .prompt()
                .system("你是一个专业的AI编程助手，请用中文回答问题。")
                .messages(historyMessages(conversationId))
                .user(request.getMessage())
                .call()
                .content();
```

把 `chatStream()` 中：

```java
        Prompt prompt = buildPrompt(request.getModel(), request.getMessage(), conversationId);
        ChatModel chatModel = resolveModel(request.getModel());
```
及其 `chatModel.stream(prompt).map(...)` 流替换为：

```java
        Flux<String> textFlux = buildSkillClient(request.getModel())
                .prompt()
                .system("你是一个专业的AI编程助手，请用中文回答问题。")
                .messages(historyMessages(conversationId))
                .user(request.getMessage())
                .stream()
                .content();
```

并把 `Flux.concat(Flux.just(initEvent), chatModel.stream(prompt).map(...).filter(...).map(...).doOnComplete(...).doOnError(...))` 改为基于 `textFlux`：

```java
        return Flux.concat(
                Flux.just(initEvent),
                textFlux
                        .filter(text -> !text.isEmpty())
                        .map(text -> ServerSentEvent.<String>builder()
                                .data(text)
                                .build())
                        .doOnComplete(() -> {
                            ChatMessage assistantMsg = ChatMessage.builder()
                                    .conversationId(convId)
                                    .role("ASSISTANT")
                                    .content(contentBuilder.toString())
                                    .modelId(request.getModel())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            chatMessageRepository.save(assistantMsg);
                        })
                        .doOnError(e -> log.error("流式对话异常: {}", e.getMessage()))
        );
```

新增私有方法 `historyMessages`（从原 `buildPrompt` 抽取历史装配逻辑，供 ChatClient `.messages(...)` 使用）：

```java
    private List<Message> historyMessages(Long conversationId) {
        List<Message> messages = new ArrayList<>();
        if (conversationId != null) {
            List<ChatMessage> history = chatMessageRepository
                    .findByConversationIdOrderByCreatedAtAsc(conversationId);
            for (ChatMessage msg : history) {
                switch (msg.getRole()) {
                    case "USER" -> messages.add(new UserMessage(msg.getContent()));
                    case "ASSISTANT" -> messages.add(new AssistantMessage(msg.getContent()));
                    case "SYSTEM" -> messages.add(new SystemMessage(msg.getContent()));
                }
            }
        }
        return messages;
    }
```

删除现已无引用的 `buildPrompt(...)` 方法。

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn clean compile -pl aicoder-chat -DskipTests`
Expected: BUILD SUCCESS。若编译失败（如 `ChatClient.stream().content()` 或 `.defaultTools(...)` 签名不匹配），按 Spring AI 1.1.2 API 调整：`.defaultTools(Object...)` 接受带 `@Tool` 的 bean；`.stream().content()` 返回 `Flux<String>`。

- [ ] **Step 3: 运行 chat 模块既有测试确认不退化**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn test -pl aicoder-chat`
Expected: BUILD SUCCESS，本计划新增的 3 个测试类 + 既有测试全绿。

- [ ] **Step 4: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java
git commit -m "feat(chat): ChatService 接入 ChatClient + 技能目录注入 + read_skill"
```

---

### Task 6: application.yml（Nacos 共享配置 + skill.directory）

**Files:**
- Modify: `aicoder-chat/src/main/resources/application.yml`

- [ ] **Step 1: 读当前 application.yml，在 `spring:` 块加 config.import + nacos server-addr，新增 `skill:` 块**

在 `spring:` 下加（与 aicoder-skill 同构，保持 YAML 缩进与同级键一致）：

```yaml
  config:
    import:
      # 共享配置：与 aicoder-skill 读同一份 skill.directory
      - optional:nacos:aicoder-shared.yml
  cloud:
    nacos:
      server-addr: localhost:8848
```

在文件末尾（`springdoc:` 之外）新增：

```yaml
# 技能共享目录：本地兜底，可被 Nacos aicoder-shared.yml 或 SKILL_DIRECTORY 覆盖
skill:
  directory: ${SKILL_DIRECTORY:./skills}
```

- [ ] **Step 2: 编译 + 全模块测试**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn clean test -pl aicoder-chat`
Expected: BUILD SUCCESS，全部单测通过（`optional:` 保证无 Nacos 时单测仍跑；chat 单测不启 Spring 上下文）。

- [ ] **Step 3: Commit**

```bash
git add aicoder-chat/src/main/resources/application.yml
git commit -m "feat(chat): application.yml 接入 Nacos aicoder-shared.yml + skill.directory"
```

---

### Task 7: 端到端验证（需中间件）

> 需 MySQL/Redis/Nacos(3.x)/Gateway/chat 运行。记录验证结果。

- [ ] **Step 1: 确保 greeting-skill 已物化到共享目录**

共享目录 = Nacos `aicoder-shared.yml` 的 `skill.directory`（仓库根 `skills/`）。检查：

```bash
ls /Users/haijingxu/workspace/claudeCode/aicoder/skills/greeting-skill/SKILL.md
```

若不存在（skill 服务的播种是幂等 skip，可能未物化），启动 aicoder-skill 触发，或经网关重新 approve 触发物化：

```bash
# 启动 skill（物化 ACTIVE 技能；greeting-skill 已 ACTIVE 但 filePath 可能未物化）
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
nohup $JAVA_HOME/bin/java -jar aicoder-skill/target/aicoder-skill-1.0.0-SNAPSHOT.jar > /tmp/aicoder_skill.log 2>&1 &
```

确认 `[skill] directory` 日志指向 Nacos 路径，且 `skills/greeting-skill/SKILL.md` 存在。

- [ ] **Step 2: 构建 + 启动 gateway + chat**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /Users/haijingxu/software/apache-maven-3.9.16/bin/mvn clean package -pl aicoder-gateway,aicoder-chat -am -DskipTests
nohup $JAVA_HOME/bin/java -jar aicoder-gateway/target/aicoder-gateway-1.0.0-SNAPSHOT.jar > /tmp/aicoder_gw.log 2>&1 &
sleep 3
nohup $JAVA_HOME/bin/java -jar aicoder-chat/target/aicoder-chat-1.0.0-SNAPSHOT.jar > /tmp/aicoder_chat.log 2>&1 &
```

等待 ~25s，确认 :8080、:8082 OPEN，chat 注册 Nacos。

- [ ] **Step 3: 验证 chat 启动日志读取到 Nacos 技能目录**

```bash
grep "\[skill\] directory" /tmp/aicoder_chat.log
```
Expected: `configured='/Users/haijingxu/workspace/claudeCode/aicoder/skills'`（Nacos 值，非 `./skills`）。

- [ ] **Step 4: 验证技能目录注入 + read_skill（需支持 function-calling 的模型）**

用 admin 登录拿 JWT（secret `aicoder-jwt-secret-key-2024-must-be-at-least-256-bits-long`），用一个支持工具调用的模型（如 deepseek）经网关发起对话：

```bash
TOKEN=<JWT>
curl -s -X POST http://localhost:8080/api/chat/send -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"model":"deepseek-chat","message":"你有哪些技能？能用它们做什么？"}'
```

Expected: 回复应提及 `greeting-skill`（来自注入目录）；若模型进一步询问其用法，应能通过 `read_skill("greeting-skill")` 加载内容并描述。日志可确认 advisor 注入与 read_skill 触发。

- [ ] **Step 5: 验证普通对话未退化**

发起一条与技能无关的普通编程问题，确认正常作答（ChatClient 重构未破坏主路径）。

- [ ] **Step 6: 清理**

```bash
pkill -f "aicoder-gateway-1.0.0-SNAPSHOT.jar"; pkill -f "aicoder-chat-1.0.0-SNAPSHOT.jar"; pkill -f "aicoder-skill-1.0.0-SNAPSHOT.jar"
```
如全通过，无需提交；如发现问题修复后提交并说明。

---

## Self-Review 结论

- **Spec 覆盖**：§4 组件（SkillProperties/SkillRuntimeConfig/ReadSkillTool/ChatService/application/pom）→ Task 1-6 全覆盖；§5 决策（只接 ChatService、read_skill 自建、目录共享、ChatClient 按请求构建、模型工具调用前置）→ 各 Task 体现 + Task 7 Step 4 验收；§7 测试（ReadSkillTool/SkillRuntimeConfig 单测 + 端到端）→ Task 3/4/7。✅
- **占位符**：无 TBD/TODO；每步含完整代码或确切命令。
- **类型一致**：`SkillRegistry.contains/readSkillContent/readSkillContent`、`FileSystemSkillRegistry.builder().projectSkillsDirectory`、`SkillPromptAugmentAdvisor.builder().skillRegistry/getSkillCount`、`ReadSkillTool.readSkill`、`SkillProperties.getResolvedDirectory` 全文与 graph-core 1.1.2.2 实际 API（已反查源码）一致。
- **关键风险**：ChatService 重构主路径（Task 5）→ Task 5 Step 2-3 编译+既有测试 + Task 7 端到端覆盖；模型工具调用能力 → Task 7 Step 4 以支持 function-calling 的模型验收、非支持模型降级为只注入目录（设计 §5.5）。
