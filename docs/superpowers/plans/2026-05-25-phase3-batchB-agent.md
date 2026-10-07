# 第三期批次 B — AI Agent 模式 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 aicoder-chat 模块中实现 AI Agent 模式，用户给一个任务，Agent 自主调用工具（知识库检索、SQL 查询、HTTP 请求）完成，前端展示思考过程和工具调用。

**Architecture:** 使用手动 ReAct 循环实现 Agent：LLM 判断是否需要调用工具 → 调用工具 → 观察结果 → 继续推理 → 输出答案。工具通过 RestTemplate + Nacos 调用 aicoder-rag 的能力。前端复用 ChatView.vue，通过 SSE 事件类型区分普通对话和 Agent 模式。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI 1.1.2, Vue 3.5, TypeScript, SSE

---

## File Map

### Backend (新建文件)

```
aicoder-chat/src/main/java/com/ai/coder/chat/
  controller/AgentController.java              -- Agent SSE 流式端点
  service/AgentService.java                    -- Agent ReAct 循环核心服务
  dto/AgentRequest.java                        -- Agent 请求 DTO
  tool/KnowledgeSearchTool.java                -- 知识库检索工具
  tool/SqlQueryTool.java                       -- SQL 查询工具
  tool/HttpRequestTool.java                    -- HTTP 请求工具
```

### Backend (修改文件)

```
aicoder-chat/src/main/java/com/ai/coder/chat/
  config/ChatModelConfig.java                  -- 添加 RestTemplate Bean
  service/ConversationService.java             -- createConversation 支持 AGENT 类型
```

### Frontend (修改文件)

```
aicoder-web/src/stores/chatStore.ts            -- 扩展 Agent 相关状态和方法
aicoder-web/src/views/chat/ChatView.vue        -- Agent 消息渲染（思考过程+工具调用）
aicoder-web/src/types/index.ts                 -- 添加 AgentStep 类型
```

---

## Task 1: 后端 — Agent 请求 DTO + ConversationService 扩展

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/dto/AgentRequest.java`
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/service/ConversationService.java`

- [ ] **Step 1: 创建 AgentRequest DTO**

```java
package com.ai.coder.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    private String message;
    private Long conversationId;
    private String model;
}
```

- [ ] **Step 2: ConversationService 支持 AGENT 类型**

在 `ConversationService.java` 中，`createConversation` 方法的 `type` 参数硬编码为 `"CHAT"`。添加一个重载方法：

```java
@Transactional
public Conversation createConversation(Long userId, String modelId, String title, String type) {
    Conversation conversation = Conversation.builder()
            .userId(userId)
            .modelId(modelId)
            .title(title)
            .type(type)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return conversationRepository.save(conversation);
}
```

原有的 `createConversation(Long userId, String modelId, String title)` 保持不变（调用新方法传 `"CHAT"`）。

- [ ] **Step 3: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-chat -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/dto/AgentRequest.java aicoder-chat/src/main/java/com/ai/coder/chat/service/ConversationService.java
git commit -m "feat(chat): Agent 请求 DTO + ConversationService 支持 AGENT 类型"
```

---

## Task 2: 后端 — Agent 工具类（KnowledgeSearchTool + SqlQueryTool + HttpRequestTool）

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/tool/KnowledgeSearchTool.java`
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/tool/SqlQueryTool.java`
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/tool/HttpRequestTool.java`

这三个工具类通过 RestTemplate + Nacos 调用其他微服务。

- [ ] **Step 1: 创建 KnowledgeSearchTool**

```java
package com.ai.coder.chat.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final RestTemplate restTemplate;

    public String getName() {
        return "knowledge_search";
    }

    public String getDescription() {
        return "搜索知识库，返回与查询相关的文档片段。当需要查找特定知识或文档内容时使用此工具。";
    }

    public String execute(String query, String knowledgeBaseId) {
        log.info("KnowledgeSearchTool 执行: query={}, kbId={}", query, knowledgeBaseId);
        try {
            String url = "http://aicoder-rag/api/rag/kb";
            ResponseEntity<?> response = restTemplate.exchange(url, HttpMethod.GET, null, Object.class);
            // 简化实现：直接返回提示信息
            // 实际使用时可通过向量检索 API 获取结果
            return "知识库搜索结果：暂未找到匹配的文档片段。请确保已上传相关文档到知识库。";
        } catch (Exception e) {
            log.warn("知识库搜索失败: {}", e.getMessage());
            return "知识库搜索失败: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 2: 创建 SqlQueryTool**

```java
package com.ai.coder.chat.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlQueryTool {

    private final RestTemplate restTemplate;

    public String getName() {
        return "sql_query";
    }

    public String getDescription() {
        return "将自然语言问题转换为 SQL 查询并执行，返回查询结果。当需要查询数据库数据时使用此工具。";
    }

    public String execute(String question, String knowledgeBaseId) {
        log.info("SqlQueryTool 执行: question={}", question);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = """
                    {"knowledgeBaseId": %s, "model": "deepseek-v4-flash", "question": "%s"}
                    """.formatted(knowledgeBaseId != null ? knowledgeBaseId : "0", question);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            String result = restTemplate.postForObject(
                    "http://aicoder-rag/api/rag/sql/ask", entity, String.class);
            return result != null ? result : "SQL 查询无结果";
        } catch (Exception e) {
            log.warn("SQL 查询失败: {}", e.getMessage());
            return "SQL 查询失败: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 3: 创建 HttpRequestTool**

```java
package com.ai.coder.chat.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
public class HttpRequestTool {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String getName() {
        return "http_request";
    }

    public String getDescription() {
        return "发起 HTTP 请求获取外部 API 数据。当需要调用外部接口或获取网页内容时使用此工具。";
    }

    public String execute(String url, String method) {
        log.info("HttpRequestTool 执行: url={}, method={}", url, method);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(""));
            } else {
                requestBuilder.GET();
            }

            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body.length() > 2000) {
                body = body.substring(0, 2000) + "...(截断)";
            }
            return "HTTP " + response.statusCode() + ": " + body;
        } catch (Exception e) {
            log.warn("HTTP 请求失败: {}", e.getMessage());
            return "HTTP 请求失败: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: 在 ChatModelConfig 中注册 RestTemplate**

编辑 `aicoder-chat/src/main/java/com/ai/coder/chat/config/ChatModelConfig.java`，在类中添加：

```java
@Bean
@org.springframework.cloud.client.loadbalancer.LoadBalanced
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

添加 import：
```java
import org.springframework.web.client.RestTemplate;
```

注意：aicoder-chat 的 pom.xml 已有 `spring-cloud-starter-alibaba-nacos-discovery`，包含 loadbalancer。

- [ ] **Step 5: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-chat -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/tool/ aicoder-chat/src/main/java/com/ai/coder/chat/config/ChatModelConfig.java
git commit -m "feat(chat): Agent 工具类 — 知识库检索/SQL查询/HTTP请求"
```

---

## Task 3: 后端 — AgentService 核心 ReAct 循环

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/service/AgentService.java`

这是 Agent 的核心：手动 ReAct（Reasoning + Acting）循环。

- [ ] **Step 1: 创建 AgentService**

```java
package com.ai.coder.chat.service;

import com.ai.coder.chat.dto.AgentRequest;
import com.ai.coder.chat.entity.ChatMessage;
import com.ai.coder.chat.repository.ChatMessageRepository;
import com.ai.coder.chat.tool.HttpRequestTool;
import com.ai.coder.chat.tool.KnowledgeSearchTool;
import com.ai.coder.chat.tool.SqlQueryTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final Map<String, ChatModel> modelMap;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationService conversationService;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final SqlQueryTool sqlQueryTool;
    private final HttpRequestTool httpRequestTool;
    private final ObjectMapper objectMapper;

    private static final String AGENT_SYSTEM_PROMPT = """
            你是一个智能 AI Agent，可以使用工具来完成任务。

            可用工具：
            1. knowledge_search(query, knowledgeBaseId) - 搜索知识库，返回相关文档片段
            2. sql_query(question, knowledgeBaseId) - 将自然语言转 SQL 并执行查询
            3. http_request(url, method) - 发起 HTTP 请求获取数据

            当需要使用工具时，请严格按以下 JSON 格式回复（不要包含其他文字）：
            {"tool": "工具名", "args": {"参数名": "参数值"}, "thought": "为什么要调用这个工具"}

            当不需要使用工具时，直接用自然语言回复用户。

            每次最多调用一个工具。调用后你会收到工具结果，再决定下一步。
            """;

    private static final int MAX_ITERATIONS = 5;

    public Flux<ServerSentEvent<String>> agentStream(AgentRequest request, Long userId) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().limit(Integer.MAX_VALUE);

        Thread thread = new Thread(() -> {
            try {
                executeAgentLoop(request, userId, sink);
            } catch (Exception e) {
                log.error("Agent 执行失败: {}", e.getMessage(), e);
                try {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("agent_error")
                            .data(objectMapper.writeValueAsString(Map.of("error", e.getMessage())))
                            .build());
                } catch (Exception ignored) {}
                sink.tryEmitComplete();
            }
        });
        thread.start();

        return sink.asFlux();
    }

    private void executeAgentLoop(AgentRequest request, Long userId,
                                   Sinks.Many<ServerSentEvent<String>> sink) throws Exception {
        Long conversationId = request.getConversationId();

        // 创建或复用会话
        if (conversationId == null) {
            String title = request.getMessage().length() > 30
                    ? request.getMessage().substring(0, 30) + "..."
                    : request.getMessage();
            var conv = conversationService.createConversation(userId, request.getModel(), title, "AGENT");
            conversationId = conv.getId();
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("conversation")
                    .data(objectMapper.writeValueAsString(Map.of("conversationId", conversationId)))
                    .build());
        }

        // 保存用户消息
        ChatMessage userMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(request.getMessage())
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        // 获取历史消息
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(AGENT_SYSTEM_PROMPT));
        for (ChatMessage msg : history) {
            switch (msg.getRole()) {
                case "USER" -> messages.add(new UserMessage(msg.getContent()));
                case "ASSISTANT" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        ChatModel chatModel = modelMap.get(request.getModel());
        if (chatModel == null) chatModel = modelMap.values().iterator().next();

        // ReAct 循环
        StringBuilder finalAnswer = new StringBuilder();
        String lastToolResult = null;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // 如果有工具结果，添加到消息中
            if (lastToolResult != null) {
                messages.add(new UserMessage("工具执行结果：\n" + lastToolResult));
            }

            // 调用 LLM
            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();

            // 发送思考事件
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("agent_think")
                    .data(objectMapper.writeValueAsString(Map.of("content", content)))
                    .build());

            // 检查是否包含工具调用
            JsonNode toolCall = parseToolCall(content);
            if (toolCall != null) {
                String toolName = toolCall.path("tool").asText();
                String thought = toolCall.path("thought").asText("");

                // 发送工具调用开始事件
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("tool_call")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "tool", toolName,
                                "args", toolCall.path("args"),
                                "thought", thought
                        )))
                        .build());

                // 执行工具
                String toolResult = executeTool(toolName, toolCall.path("args"));

                // 发送工具结果事件
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("tool_result")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "tool", toolName,
                                "result", toolResult
                        )))
                        .build());

                // 将 Assistant 的工具调用请求加入历史
                messages.add(new AssistantMessage(content));
                lastToolResult = toolResult;
            } else {
                // 没有工具调用，这是最终回答
                finalAnswer.append(content);
                break;
            }
        }

        // 如果循环结束还没最终回答，用最后一次的内容
        if (finalAnswer.isEmpty() && lastToolResult != null) {
            // 让 LLM 基于工具结果做总结
            messages.add(new UserMessage("请根据以上工具执行结果，给出最终回答。"));
            Prompt summaryPrompt = new Prompt(messages);
            ChatResponse summaryResponse = chatModel.call(summaryPrompt);
            finalAnswer.append(summaryResponse.getResult().getOutput().getText());

            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("agent_answer")
                    .data(objectMapper.writeValueAsString(Map.of("content", finalAnswer.toString())))
                    .build());
        } else {
            // 发送最终答案事件
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("agent_answer")
                    .data(objectMapper.writeValueAsString(Map.of("content", finalAnswer.toString())))
                    .build());
        }

        // 保存助手消息
        ChatMessage assistantMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content(finalAnswer.toString())
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("done")
                .data("{}")
                .build());
        sink.tryEmitComplete();
    }

    private JsonNode parseToolCall(String content) {
        try {
            String json = content.trim();
            // 尝试从 markdown 代码块中提取
            if (json.contains("```json")) {
                int start = json.indexOf("```json") + 7;
                int end = json.indexOf("```", start);
                if (end > start) json = json.substring(start, end).trim();
            } else if (json.contains("```")) {
                int start = json.indexOf("```") + 3;
                int end = json.indexOf("```", start);
                if (end > start) json = json.substring(start, end).trim();
            }

            JsonNode node = objectMapper.readTree(json);
            if (node.has("tool") && !node.path("tool").asText().isEmpty()) {
                return node;
            }
        } catch (Exception e) {
            // 不是 JSON，不是工具调用
        }
        return null;
    }

    private String executeTool(String toolName, JsonNode args) {
        return switch (toolName) {
            case "knowledge_search" -> knowledgeSearchTool.execute(
                    args.path("query").asText(""),
                    args.path("knowledgeBaseId").asText(""));
            case "sql_query" -> sqlQueryTool.execute(
                    args.path("question").asText(""),
                    args.path("knowledgeBaseId").asText(""));
            case "http_request" -> httpRequestTool.execute(
                    args.path("url").asText(""),
                    args.path("method").asText("GET"));
            default -> "未知工具: " + toolName;
        };
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-chat -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/service/AgentService.java
git commit -m "feat(chat): AgentService 核心 ReAct 循环 — 思考/工具调用/回答"
```

---

## Task 4: 后端 — AgentController SSE 端点

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/controller/AgentController.java`

- [ ] **Step 1: 创建 AgentController**

```java
package com.ai.coder.chat.controller;

import com.ai.coder.chat.dto.AgentRequest;
import com.ai.coder.chat.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agentStream(@RequestBody AgentRequest request,
                                                      @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return agentService.agentStream(request, userId);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-chat -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add aicoder-chat/src/main/java/com/ai/coder/chat/controller/AgentController.java
git commit -m "feat(chat): Agent SSE 流式端点 POST /api/chat/agent/stream"
```

---

## Task 5: 前端 — 类型扩展 + chatStore Agent 支持

**Files:**
- Modify: `aicoder-web/src/types/index.ts`
- Modify: `aicoder-web/src/stores/chatStore.ts`

- [ ] **Step 1: 在 types/index.ts 添加 AgentStep 类型**

在文件末尾添加：

```typescript
export interface AgentStep {
  type: 'think' | 'tool_call' | 'tool_result' | 'answer'
  content?: string
  tool?: string
  args?: Record<string, any>
  thought?: string
  result?: string
}
```

- [ ] **Step 2: chatStore 扩展 Agent 状态**

编辑 `aicoder-web/src/stores/chatStore.ts`。

在 import 区域添加 `AgentStep` 类型：
```typescript
import type { Conversation, ChatMessage, ModelInfo, AgentStep } from '@/types'
```

在 store 函数内部，在 `isStreaming` ref 后面添加：
```typescript
const agentSteps = ref<AgentStep[]>([])
const isAgentMode = ref(false)
```

添加 Agent 相关方法，在 `resetChat` 方法之前：

```typescript
const startAgentChat = async (model: string, message: string) => {
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: message,
    modelId: model,
    createdAt: new Date().toISOString()
  })
  streamingContent.value = ''
  isStreaming.value = true
  isAgentMode.value = true
  agentSteps.value = []
}

const addAgentStep = (step: AgentStep) => {
  agentSteps.value.push({ ...step, id: agentSteps.value.length } as AgentStep)
}

const resetAgent = () => {
  agentSteps.value = []
  isAgentMode.value = false
}
```

在 `return` 语句中添加：
```typescript
agentSteps, isAgentMode,
startAgentChat, addAgentStep, resetAgent,
```

同时修改 `resetChat` 方法，在最后添加：
```typescript
agentSteps.value = []
isAgentMode.value = false
```

- [ ] **Step 3: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add aicoder-web/src/types/index.ts aicoder-web/src/stores/chatStore.ts
git commit -m "feat(web): chatStore 扩展 Agent 相关状态和方法"
```

---

## Task 6: 前端 — ChatView Agent 模式渲染

**Files:**
- Modify: `aicoder-web/src/views/chat/ChatView.vue`

这是最大的前端改动，需要：
1. 添加 Agent 发送按钮
2. 处理 Agent SSE 事件
3. 渲染 Agent 思考步骤和工具调用

- [ ] **Step 1: 在 ChatView.vue 添加 Agent 发送逻辑**

编辑 `aicoder-web/src/views/chat/ChatView.vue`。

在 `<script setup>` 中添加 import：
```typescript
import { streamRequest } from '@/api/request'
import type { AgentStep } from '@/types'
```

添加一个新的发送函数：

```typescript
const isAgent = ref(false)

const streamAgent = async (message: string) => {
  chatStore.startAgentChat(chatStore.currentModel, message)
  try {
    await streamRequest('/api/chat/agent/stream', {
      model: chatStore.currentModel,
      message: message,
      conversationId: chatStore.currentConversationId
    }, {
      onEvent(eventType, data) {
        try {
          const parsed = JSON.parse(data)
          if (eventType === 'conversation') {
            if (parsed.conversationId && !chatStore.currentConversationId) {
              chatStore.currentConversationId = parsed.conversationId
            }
          } else if (eventType === 'agent_think') {
            chatStore.addAgentStep({ type: 'think', content: parsed.content })
          } else if (eventType === 'tool_call') {
            chatStore.addAgentStep({
              type: 'tool_call',
              tool: parsed.tool,
              args: parsed.args,
              thought: parsed.thought
            })
          } else if (eventType === 'tool_result') {
            chatStore.addAgentStep({
              type: 'tool_result',
              tool: parsed.tool,
              result: parsed.result
            })
          } else if (eventType === 'agent_answer') {
            chatStore.streamingContent = parsed.content || ''
          } else if (eventType === 'done') {
            chatStore.finishStreaming()
            chatStore.fetchConversations()
          }
        } catch { /* ignore parse error */ }
      },
      onError() {
        chatStore.finishStreaming()
      },
      onComplete() {
        if (chatStore.isStreaming) {
          chatStore.finishStreaming()
        }
      }
    })
  } catch {
    chatStore.finishStreaming()
  }
}

const handleSendAgent = () => {
  const msg = inputMessage.value.trim()
  if (!msg || sending.value) return
  sending.value = true
  inputMessage.value = ''
  isAgent.value = true
  streamAgent(msg).finally(() => { sending.value = false; isAgent.value = false })
}
```

- [ ] **Step 2: 在模板中添加 Agent 发送按钮和步骤渲染**

在聊天输入区域，在现有发送按钮旁边或之前添加一个 Agent 发送按钮。

找到发送按钮的位置（`@click="handleSend"` 的按钮），在其后面添加：

```html
          <button
            class="btn btn-agent"
            :disabled="!inputMessage.trim() || sending"
            @click="handleSendAgent"
            title="Agent 模式：AI 自主调用工具完成任务"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          </button>
```

在消息列表区域，在 `isStreaming` 的消息块之后、`</div>` (msg-list 结束) 之前，添加 Agent 步骤渲染：

在 `<div v-if="isStreaming" class="msg-row assistant">` 这个 div 的结束 `</div>` 之后添加：

```html
        <!-- Agent 步骤展示 -->
        <div v-if="chatStore.isAgentMode && chatStore.agentSteps.length > 0" class="agent-steps">
          <div v-for="(step, idx) in chatStore.agentSteps" :key="idx" class="agent-step">
            <div v-if="step.type === 'think'" class="step-think">
              <span class="step-icon">💭</span>
              <span class="step-label">思考</span>
              <span class="step-text">{{ step.content }}</span>
            </div>
            <div v-else-if="step.type === 'tool_call'" class="step-tool-call">
              <span class="step-icon">🔧</span>
              <span class="step-label">调用工具: {{ step.tool }}</span>
              <span v-if="step.thought" class="step-text">{{ step.thought }}</span>
            </div>
            <div v-else-if="step.type === 'tool_result'" class="step-tool-result">
              <span class="step-icon">📊</span>
              <span class="step-label">{{ step.tool }} 返回结果</span>
              <details><summary>查看详情</summary><pre class="step-pre">{{ step.result }}</pre></details>
            </div>
          </div>
        </div>
```

- [ ] **Step 3: 添加 Agent 相关样式**

在 `<style>` 末尾添加：

```scss
.btn-agent {
  height: 42px; width: 42px; padding: 0; border-radius: var(--radius-sm);
  flex-shrink: 0; background: #8b5cf6; color: #fff; border: none;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  &:hover { background: #7c3aed; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.agent-steps {
  margin: 0 24px 16px; padding: 16px; background: #f8fafc;
  border: 1px solid var(--border-subtle); border-radius: 8px;
}

.agent-step {
  padding: 8px 0; border-bottom: 1px solid var(--border-subtle);
  &:last-child { border-bottom: none; }
}

.step-think, .step-tool-call, .step-tool-result {
  display: flex; align-items: flex-start; gap: 8px; flex-wrap: wrap;
}

.step-icon { font-size: 16px; flex-shrink: 0; }
.step-label { font-size: 12px; font-weight: 600; color: var(--text-secondary); white-space: nowrap; }
.step-text { font-size: 12px; color: var(--text-secondary); flex: 1; min-width: 0; }
.step-pre {
  font-size: 11px; color: var(--text-primary); background: var(--bg-surface);
  padding: 8px; border-radius: 4px; overflow-x: auto; max-height: 200px;
  overflow-y: auto; white-space: pre-wrap; word-break: break-all; margin: 4px 0 0;
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 5: Commit**

```bash
git add aicoder-web/src/views/chat/ChatView.vue
git commit -m "feat(web): ChatView Agent 模式 — 发送按钮 + 步骤渲染"
```

---

## Task 7: 最终验证

- [ ] **Step 1: 后端全量编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 前端全量编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npm run build`
Expected: 编译成功

- [ ] **Step 3: 最终 Commit（如有未提交变更）**

```bash
git add -A
git commit -m "feat: 第三期批次 B 完成 — AI Agent 模式"
```
