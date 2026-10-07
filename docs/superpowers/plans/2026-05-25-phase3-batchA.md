# 第三期批次 A — 补齐遗留 + 仪表盘 + 工作流模板 + 子工作流/循环 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐前后端遗留问题，新增首页仪表盘、工作流模板、子工作流/循环节点。

**Architecture:** 前端统一 SSE 流式封装到 `request.ts`；后端各微服务新增统计端点由 admin 聚合；工作流模板为独立 CRUD；子工作流/循环作为新 NodeAction 实现。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI 1.1.2, Vue 3.5, TypeScript, Vue Flow, Pinia

---

## File Map

### Backend (新建文件)

```
aicoder-admin/src/main/java/com/ai/coder/admin/
  controller/DashboardController.java              -- 仪表盘聚合接口
  service/DashboardService.java                    -- 聚合调用各微服务统计
  dto/DashboardStatsResponse.java                  -- 统计响应 DTO
  dto/UpdateUserRequest.java                       -- 用户信息更新请求

aicoder-chat/src/main/java/com/ai/coder/chat/
  controller/StatsController.java                  -- 对话统计端点

aicoder-rag/src/main/java/com/ai/coder/rag/
  controller/StatsController.java                  -- 知识库统计端点

aicoder-workflow/src/main/java/com/ai/coder/workflow/
  controller/StatsController.java                  -- 工作流统计端点
  node/SubWorkflowNode.java                        -- 子工作流节点
  node/LoopNode.java                               -- 循环节点
  model/entity/WorkflowTemplate.java               -- 工作流模板实体
  repository/WorkflowTemplateRepository.java       -- 模板 Repository
  service/WorkflowTemplateService.java             -- 模板管理服务
  controller/WorkflowTemplateController.java       -- 模板 REST 接口
```

### Backend (修改文件)

```
aicoder-admin/src/main/java/com/ai/coder/admin/
  controller/AuthController.java                   -- 新增 PUT /user/info
  service/AuthService.java                         -- 新增 updateUserInfo
  pom.xml                                          -- 新增 WebClient 依赖（如缺失）

aicoder-workflow/src/main/java/com/ai/coder/workflow/
  service/WorkflowNodeFactory.java                 -- 添加 subworkflow/loop 分支
  service/WorkflowExecutionService.java            -- KeyStrategy 收集 loop 节点字段
```

### Frontend (新建文件)

```
aicoder-web/src/api/
  dashboard.ts                                     -- 仪表盘 API
  vectorDb.ts                                      -- 重命名自 rag.ts
aicoder-web/src/views/workflow/components/nodes/
  SubWorkflowNode.vue                              -- 子工作流画布节点
  LoopNode.vue                                     -- 循环画布节点
```

### Frontend (修改文件)

```
aicoder-web/src/api/request.ts                     -- 新增 streamRequest 通用方法
aicoder-web/src/api/rag.ts                         -- 重写为 RAG 对话 API
aicoder-web/src/api/auth.ts                        -- 新增 updateUserInfo
aicoder-web/src/api/workflow.ts                    -- 新增模板 API
aicoder-web/src/types/workflow.ts                  -- 扩展 WorkflowNodeConfig
aicoder-web/src/views/HomeView.vue                 -- 重写为仪表盘
aicoder-web/src/views/rag/RagChatView.vue          -- 使用统一流式 API
aicoder-web/src/views/workflow/WorkflowListView.vue -- 新增从模板创建
aicoder-web/src/views/workflow/components/FlowCanvas.vue    -- 注册新节点
aicoder-web/src/views/workflow/components/NodePanel.vue     -- 添加新拖拽项
aicoder-web/src/views/workflow/components/ConfigPanel.vue   -- 新节点配置表单
```

### SQL

```sql
-- 新增到 schema.sql
CREATE TABLE IF NOT EXISTS ai_workflow_template (...)
```

---

## Task 1: 后端 — 用户资料编辑接口

**Files:**
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/UpdateUserRequest.java`
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/controller/AuthController.java`
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/service/AuthService.java`

- [ ] **Step 1: 创建 UpdateUserRequest DTO**

```java
package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    private String nickname;
    private String email;
    private String avatar;
}
```

- [ ] **Step 2: AuthService 添加 updateUserInfo 方法**

在 `AuthService.java` 的 `getUserInfo` 方法后添加：

```java
public void updateUserInfo(Long userId, String nickname, String email, String avatar) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    if (nickname != null) user.setNickname(nickname);
    if (email != null) user.setEmail(email);
    if (avatar != null) user.setAvatar(avatar);
    user.setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);
}
```

需要在文件顶部已有 `import java.time.LocalDateTime;`。

- [ ] **Step 3: AuthController 添加 PUT 端点**

在 `AuthController.java` 中添加 import：

```java
import com.ai.coder.admin.dto.UpdateUserRequest;
import org.springframework.web.bind.annotation.PutMapping;
```

在 `getUserInfo` 方法后添加：

```java
@PutMapping("/user/info")
public ResponseEntity<UserInfoResponse> updateUserInfo(
        @RequestHeader("Authorization") String authorization,
        @RequestBody UpdateUserRequest request) {
    String token = authorization.replace("Bearer ", "");
    DecodedJWT jwt = jwtUtil.parseToken(token);
    Long userId = jwt.getClaim("userId").asLong();
    authService.updateUserInfo(userId, request.getNickname(), request.getEmail(), request.getAvatar());
    UserInfoResponse userInfo = authService.getUserInfo(userId);
    return ResponseEntity.ok(userInfo);
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add aicoder-admin/src/main/java/com/ai/coder/admin/dto/UpdateUserRequest.java aicoder-admin/src/main/java/com/ai/coder/admin/controller/AuthController.java aicoder-admin/src/main/java/com/ai/coder/admin/service/AuthService.java
git commit -m "feat(admin): 新增用户资料编辑接口 PUT /api/admin/user/info"
```

---

## Task 2: 前端 — 流式 API 统一封装

**Files:**
- Modify: `aicoder-web/src/api/request.ts`

- [ ] **Step 1: 在 request.ts 中新增 streamRequest 方法**

在文件末尾 `export default request` 之后追加：

```typescript
export interface StreamCallbacks {
  onEvent: (eventType: string, data: string) => void
  onError?: (error: Error) => void
  onComplete?: () => void
}

export async function streamRequest(url: string, body: Record<string, any>, callbacks: StreamCallbacks): Promise<void> {
  const token = localStorage.getItem('token')
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body)
  })

  if (!response.ok) {
    const err = new Error(`请求失败: ${response.status}`)
    callbacks.onError?.(err)
    return
  }

  if (!response.body) {
    const err = new Error('不支持流式输出')
    callbacks.onError?.(err)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = ''
  let dataLines: string[] = []

  const flushEvent = () => {
    if (dataLines.length === 0) return
    const fullData = dataLines.join('\n')
    dataLines = []
    if (fullData && fullData !== '[DONE]') {
      callbacks.onEvent(currentEvent, fullData)
    }
    currentEvent = ''
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          flushEvent()
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5))
        } else if (line === '') {
          flushEvent()
        }
      }
    }

    if (buffer.startsWith('data:')) {
      dataLines.push(buffer.slice(5))
    }
    flushEvent()
    callbacks.onComplete?.()
  } catch (e) {
    callbacks.onError?.(e instanceof Error ? e : new Error(String(e)))
  }
}
```

- [ ] **Step 2: 验证前端编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add aicoder-web/src/api/request.ts
git commit -m "feat(web): 统一 SSE 流式请求封装 streamRequest"
```

---

## Task 3: 前端 — RAG 对话 API + 重命名

**Files:**
- Rename: `aicoder-web/src/api/rag.ts` → `aicoder-web/src/api/vectorDb.ts`
- Create: `aicoder-web/src/api/rag.ts` (新文件，RAG 对话 API)
- Modify: `aicoder-web/src/views/admin/VectorDbConfigView.vue` (更新 import)

- [ ] **Step 1: 重命名 rag.ts 为 vectorDb.ts**

Run: `mv aicoder-web/src/api/rag.ts aicoder-web/src/api/vectorDb.ts`

- [ ] **Step 2: 更新 VectorDbConfigView.vue 的 import**

在 `VectorDbConfigView.vue` 中找到 `import { vectorDbApi } from '@/api/rag'` 替换为：

```typescript
import { vectorDbApi } from '@/api/vectorDb'
```

- [ ] **Step 3: 创建新的 rag.ts (RAG 对话 API)**

```typescript
import { streamRequest } from './request'

export const ragChatApi = {
  streamRagChat: (knowledgeBaseId: number, model: string, message: string,
    onEvent: (eventType: string, data: string) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ) => {
    return streamRequest('/api/rag/chat/stream', {
      knowledgeBaseId,
      model,
      message
    }, { onEvent, onError, onComplete })
  }
}
```

- [ ] **Step 4: 重写 RagChatView.vue 使用统一流式 API**

将 `RagChatView.vue` 的 `<script setup>` 中的 `handleSend` 方法内手动 fetch 调用替换为使用 `ragChatApi`。

在 import 区域添加：

```typescript
import { ragChatApi } from '@/api/rag'
```

将 `handleSend` 中的 try 块替换为：

```typescript
try {
    await ragChatApi.streamRagChat(
      selectedKbId.value!,
      selectedModel.value,
      msg,
      (eventType, data) => {
        if (data && data !== '[DONE]') {
          streamingContent.value += data
        }
      },
      () => {
        streamingContent.value += '\n\n*连接出错，请重试*'
      }
    )
  } catch {
    streamingContent.value += '\n\n*连接出错，请重试*'
  }
```

- [ ] **Step 5: 验证前端编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 6: Commit**

```bash
git add aicoder-web/src/api/ aicoder-web/src/views/rag/RagChatView.vue aicoder-web/src/views/admin/VectorDbConfigView.vue
git commit -m "feat(web): RAG 对话 API 封装 + 重命名 rag.ts 为 vectorDb.ts"
```

---

## Task 4: 前端 — 用户资料编辑 API + auth.ts

**Files:**
- Modify: `aicoder-web/src/api/auth.ts`

- [ ] **Step 1: auth.ts 新增 updateUserInfo**

在 `authApi` 对象中 `getUserInfo` 方法后添加：

```typescript
  updateUserInfo: (data: { nickname?: string; email?: string; avatar?: string }) =>
    request.put<User>('/admin/user/info', data),
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add aicoder-web/src/api/auth.ts
git commit -m "feat(web): auth API 新增 updateUserInfo"
```

---

## Task 5: 后端 — 各微服务统计端点

**Files:**
- Create: `aicoder-chat/src/main/java/com/ai/coder/chat/controller/StatsController.java`
- Create: `aicoder-rag/src/main/java/com/ai/coder/rag/controller/StatsController.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/controller/StatsController.java`

- [ ] **Step 1: aicoder-chat 统计端点**

```java
package com.ai.coder.chat.controller;

import com.ai.coder.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class StatsController {

    private final ConversationRepository conversationRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        long count = conversationRepository.countByUserId(userId);
        return Map.of("conversationCount", count);
    }
}
```

需要在 `ConversationRepository.java` 中添加方法：

```java
long countByUserId(Long userId);
```

- [ ] **Step 2: aicoder-rag 统计端点**

```java
package com.ai.coder.rag.controller;

import com.ai.coder.rag.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class StatsController {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        long count = knowledgeBaseRepository.countByUserId(userId);
        return Map.of("knowledgeBaseCount", count);
    }
}
```

需要在 `KnowledgeBaseRepository.java` 中确认或添加 `countByUserId` 方法。

- [ ] **Step 3: aicoder-workflow 统计端点**

```java
package com.ai.coder.workflow.controller;

import com.ai.coder.workflow.repository.WorkflowExecutionRepository;
import com.ai.coder.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class StatsController {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        long workflowCount = workflowRepository.countByUserId(userId);
        long executionCount = executionRepository.countByUserId(userId);
        return Map.of("workflowCount", workflowCount, "executionCount", executionCount);
    }
}
```

需要在 `WorkflowRepository.java` 中添加 `long countByUserId(Long userId);`
需要在 `WorkflowExecutionRepository.java` 中添加 `long countByUserId(Long userId);`

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-chat,aicoder-rag,aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add aicoder-chat/src/ aicoder-rag/src/ aicoder-workflow/src/
git commit -m "feat: 各微服务新增 /stats 统计端点"
```

---

## Task 6: 后端 — Admin 仪表盘聚合接口

**Files:**
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/DashboardStatsResponse.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/service/DashboardService.java`
- Create: `aicoder-admin/src/main/java/com/ai/coder/admin/controller/DashboardController.java`

- [ ] **Step 1: 创建 DashboardStatsResponse**

```java
package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {
    private long conversationCount;
    private long knowledgeBaseCount;
    private long workflowCount;
    private long executionCount;
}
```

- [ ] **Step 2: 创建 DashboardService**

Admin 模块是 Spring MVC（非 WebFlux），使用 RestTemplate 调用其他微服务。由于各微服务通过 Nacos 注册，这里通过 Gateway 统一入口调用（Gateway 地址为 `localhost:8080`，admin 不经过 Gateway 直接访问）。

实际做法：admin 通过 RestTemplate + 负载均衡调用各服务名。

```java
package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RestTemplate restTemplate;

    public DashboardStatsResponse getStats(Long userId) {
        long conversationCount = 0;
        long knowledgeBaseCount = 0;
        long workflowCount = 0;
        long executionCount = 0;

        try {
            Map<String, Object> chatStats = restTemplate.getForObject(
                    "http://aicoder-chat/api/chat/stats", Map.class);
            if (chatStats != null) {
                conversationCount = ((Number) chatStats.getOrDefault("conversationCount", 0)).longValue();
            }
        } catch (Exception e) {
            log.warn("获取对话统计失败: {}", e.getMessage());
        }

        try {
            Map<String, Object> ragStats = restTemplate.getForObject(
                    "http://aicoder-rag/api/rag/stats", Map.class);
            if (ragStats != null) {
                knowledgeBaseCount = ((Number) ragStats.getOrDefault("knowledgeBaseCount", 0)).longValue();
            }
        } catch (Exception e) {
            log.warn("获取知识库统计失败: {}", e.getMessage());
        }

        try {
            Map<String, Object> wfStats = restTemplate.getForObject(
                    "http://aicoder-workflow/api/workflow/stats", Map.class);
            if (wfStats != null) {
                workflowCount = ((Number) wfStats.getOrDefault("workflowCount", 0)).longValue();
                executionCount = ((Number) wfStats.getOrDefault("executionCount", 0)).longValue();
            }
        } catch (Exception e) {
            log.warn("获取工作流统计失败: {}", e.getMessage());
        }

        return DashboardStatsResponse.builder()
                .conversationCount(conversationCount)
                .knowledgeBaseCount(knowledgeBaseCount)
                .workflowCount(workflowCount)
                .executionCount(executionCount)
                .build();
    }
}
```

需要在 admin 模块注册 RestTemplate Bean。如果 admin 模块已启用服务发现（Nacos），需要创建一个配置类：

```java
package com.ai.coder.admin.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

注意：调用时需要传递 `X-User-Id` 请求头。修改 `DashboardService` 使用带请求头的调用方式：

将 `restTemplate.getForObject(...)` 替换为：

```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

// 在 getStats 方法内：
HttpHeaders headers = new HttpHeaders();
headers.set("X-User-Id", String.valueOf(userId));
HttpEntity<Void> entity = new HttpEntity<>(headers);

ResponseEntity<Map> chatResp = restTemplate.exchange(
        "http://aicoder-chat/api/chat/stats", org.springframework.http.HttpMethod.GET, entity, Map.class);
if (chatResp.getBody() != null) {
    conversationCount = ((Number) chatResp.getBody().getOrDefault("conversationCount", 0)).longValue();
}
```

对 rag 和 workflow 的调用同理。

- [ ] **Step 3: 创建 DashboardController**

```java
package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.DashboardStatsResponse;
import com.ai.coder.admin.service.DashboardService;
import com.ai.coder.admin.util.JwtUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtUtil jwtUtil;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        DecodedJWT jwt = jwtUtil.parseToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        return ResponseEntity.ok(dashboardService.getStats(userId));
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add aicoder-admin/src/
git commit -m "feat(admin): 仪表盘统计聚合接口"
```

---

## Task 7: 前端 — 仪表盘页面

**Files:**
- Create: `aicoder-web/src/api/dashboard.ts`
- Modify: `aicoder-web/src/views/HomeView.vue`

- [ ] **Step 1: 创建 dashboard.ts**

```typescript
import request from './request'

export interface DashboardStats {
  conversationCount: number
  knowledgeBaseCount: number
  workflowCount: number
  executionCount: number
}

export const dashboardApi = {
  getStats: () => request.get<DashboardStats>('/admin/dashboard/stats')
}
```

- [ ] **Step 2: 重写 HomeView.vue**

将现有内容替换为仪表盘页面：

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi, type DashboardStats } from '@/api/dashboard'

const router = useRouter()
const stats = ref<DashboardStats | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await dashboardApi.getStats()
    stats.value = res.data
  } catch { /* 忽略 */ } finally {
    loading.value = false
  }
})

const statCards = [
  { key: 'conversationCount' as const, label: '对话数', color: '#3b82f6', icon: '💬' },
  { key: 'knowledgeBaseCount' as const, label: '知识库', color: '#8b5cf6', icon: '📚' },
  { key: 'workflowCount' as const, label: '工作流', color: '#f59e0b', icon: '⚙️' },
  { key: 'executionCount' as const, label: '执行次数', color: '#10b981', icon: '▶️' },
]

const quickActions = [
  { label: '新建对话', path: '/chat', color: '#3b82f6' },
  { label: '新建知识库', path: '/knowledge', color: '#8b5cf6' },
  { label: '新建工作流', path: '/workflow', color: '#f59e0b' },
]
</script>

<template>
  <div class="home-view">
    <div class="home-header">
      <div class="greeting">
        <h1>AI Coder</h1>
        <p>一站式 AI 开发助手平台</p>
      </div>
      <div class="quick-actions">
        <button
          v-for="action in quickActions" :key="action.path"
          class="quick-btn"
          :style="{ '--accent': action.color }"
          @click="router.push(action.path)"
        >
          {{ action.label }}
          <svg width="14" height="14" viewBox="0 0 16 16"><polyline points="6,3 11,8 6,13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="stats-grid">
      <div
        v-for="card in statCards" :key="card.key"
        class="stat-card"
        :style="{ '--card-color': card.color }"
      >
        <div class="stat-icon">{{ card.icon }}</div>
        <div class="stat-info">
          <span class="stat-value">{{ stats?.[card.key] ?? 0 }}</span>
          <span class="stat-label">{{ card.label }}</span>
        </div>
      </div>
    </div>

    <div class="module-section">
      <h3>功能模块</h3>
      <div class="module-grid">
        <div class="module-card" @click="router.push('/chat')">
          <span class="mod-icon" style="color: #3b82f6;">💬</span>
          <div>
            <h4>智能对话</h4>
            <p>多模型实时对话，流式输出</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/knowledge')">
          <span class="mod-icon" style="color: #8b5cf6;">📚</span>
          <div>
            <h4>知识库</h4>
            <p>文档上传与向量化检索</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/rag')">
          <span class="mod-icon" style="color: #10b981;">🔍</span>
          <div>
            <h4>RAG 对话</h4>
            <p>基于知识库的检索增强生成</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/workflow')">
          <span class="mod-icon" style="color: #f59e0b;">⚙️</span>
          <div>
            <h4>工作流</h4>
            <p>可视化 DAG 工作流编排</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/sql')">
          <span class="mod-icon" style="color: #06b6d4;">🗄️</span>
          <div>
            <h4>NL2SQL</h4>
            <p>自然语言转 SQL 查询</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home-view {
  max-width: 960px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

.home-header {
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.greeting {
  h1 { font-size: 24px; font-weight: 700; color: var(--text-primary); margin-bottom: 4px; }
  p { font-size: 14px; color: var(--text-secondary); }
}

.quick-actions { display: flex; gap: 10px; margin-top: 16px; }

.quick-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 8px;
  border: 1px solid var(--accent); background: transparent;
  color: var(--accent); font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.18s ease;
  &:hover { background: var(--accent); color: #fff; }
}

.loading { text-align: center; color: var(--text-muted); padding: 40px 0; }

.stats-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 32px;
}

.stat-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px; background: var(--bg-surface);
  border: 1px solid var(--border-subtle); border-radius: 10px;
  border-left: 3px solid var(--card-color);
}

.stat-icon { font-size: 28px; }

.stat-info {
  .stat-value { display: block; font-size: 22px; font-weight: 700; color: var(--text-primary); }
  .stat-label { display: block; font-size: 12px; color: var(--text-muted); margin-top: 2px; }
}

.module-section {
  h3 { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 14px; }
}

.module-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px;
}

.module-card {
  display: flex; align-items: center; gap: 12px;
  padding: 16px; background: var(--bg-surface);
  border: 1px solid var(--border-subtle); border-radius: 10px;
  cursor: pointer; transition: border-color 0.2s, transform 0.15s;
  &:hover { border-color: var(--border-default); transform: translateY(-1px); }
}

.mod-icon { font-size: 24px; flex-shrink: 0; }

.module-card {
  h4 { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 2px; }
  p { font-size: 12px; color: var(--text-secondary); margin: 0; }
}
</style>
```

- [ ] **Step 3: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add aicoder-web/src/api/dashboard.ts aicoder-web/src/views/HomeView.vue
git commit -m "feat(web): 首页仪表盘 — 统计卡片 + 功能模块入口"
```

---

## Task 8: 后端 — 工作流模板数据模型与 CRUD

**Files:**
- Modify: `sql/schema.sql`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/model/entity/WorkflowTemplate.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/repository/WorkflowTemplateRepository.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowTemplateService.java`
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/controller/WorkflowTemplateController.java`

- [ ] **Step 1: 在 schema.sql 末尾添加建表语句**

```sql
-- 11. 工作流模板表
CREATE TABLE IF NOT EXISTS ai_workflow_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) COMMENT '描述',
    category VARCHAR(50) COMMENT '分类: RAG/SQL/CHAT/AGENT',
    graph_data JSON NOT NULL COMMENT '画布数据',
    is_system TINYINT DEFAULT 0 COMMENT '1-系统预置 0-用户自建',
    user_id BIGINT COMMENT '创建人（系统模板为 NULL）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流模板表';
```

- [ ] **Step 2: 创建 WorkflowTemplate 实体**

```java
package com.ai.coder.workflow.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_workflow_template")
public class WorkflowTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "graph_data", nullable = false, columnDefinition = "JSON")
    private String graphData;

    @Column(name = "is_system")
    private Integer isSystem;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 WorkflowTemplateRepository**

```java
package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {
    List<WorkflowTemplate> findByIsSystemOrUserIdOrderByCreatedAtDesc(Integer isSystem, Long userId);
}
```

- [ ] **Step 4: 创建 WorkflowTemplateService**

```java
package com.ai.coder.workflow.service;

import com.ai.coder.workflow.model.entity.WorkflowTemplate;
import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.repository.WorkflowTemplateRepository;
import com.ai.coder.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowRepository workflowRepository;

    public List<WorkflowTemplate> listTemplates(Long userId) {
        return templateRepository.findByIsSystemOrUserIdOrderByCreatedAtDesc(1, userId);
    }

    public WorkflowTemplate createTemplate(String name, String description, String category,
                                            String graphData, Long userId) {
        WorkflowTemplate template = WorkflowTemplate.builder()
                .name(name)
                .description(description)
                .category(category)
                .graphData(graphData)
                .isSystem(0)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();
        return templateRepository.save(template);
    }

    public WorkflowTemplate createFromWorkflow(Long workflowId, Long userId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + workflowId));
        return createTemplate(workflow.getName() + "（模板）", workflow.getDescription(),
                "GENERAL", workflow.getGraphData(), userId);
    }

    public Workflow cloneFromTemplate(Long templateId, Long userId) {
        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        Workflow workflow = Workflow.builder()
                .name(template.getName())
                .description(template.getDescription())
                .graphData(template.getGraphData())
                .status("DRAFT")
                .type("WORKFLOW")
                .userId(userId)
                .build();
        return workflowRepository.save(workflow);
    }

    public void deleteTemplate(Long templateId, Long userId) {
        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        if (template.getIsSystem() == 1) {
            throw new IllegalArgumentException("系统模板不可删除");
        }
        if (!userId.equals(template.getUserId())) {
            throw new IllegalArgumentException("只能删除自己创建的模板");
        }
        templateRepository.delete(template);
    }
}
```

- [ ] **Step 5: 创建 WorkflowTemplateController**

```java
package com.ai.coder.workflow.controller;

import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.model.entity.WorkflowTemplate;
import com.ai.coder.workflow.service.WorkflowTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow/templates")
@RequiredArgsConstructor
public class WorkflowTemplateController {

    private final WorkflowTemplateService templateService;

    @GetMapping
    public List<WorkflowTemplate> list(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return templateService.listTemplates(userId);
    }

    @PostMapping
    public WorkflowTemplate create(@RequestBody Map<String, String> body,
                                    @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return templateService.createTemplate(
                body.get("name"), body.get("description"), body.get("category"),
                body.get("graphData"), userId);
    }

    @PostMapping("/{id}/clone")
    public Workflow clone(@PathVariable Long id,
                           @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return templateService.cloneFromTemplate(id, userId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id,
                        @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        templateService.deleteTemplate(id, userId);
    }
}
```

- [ ] **Step 6: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add sql/schema.sql aicoder-workflow/src/main/java/com/ai/coder/workflow/
git commit -m "feat(workflow): 工作流模板 CRUD — 实体/Repository/Service/Controller"
```

---

## Task 9: 前端 — 工作流模板 UI

**Files:**
- Modify: `aicoder-web/src/api/workflow.ts`
- Modify: `aicoder-web/src/views/workflow/WorkflowListView.vue`

- [ ] **Step 1: workflow.ts 新增模板 API**

在 `workflowApi` 对象末尾添加：

```typescript
  // 模板
  listTemplates: () => request.get<any[]>('/workflow/templates'),
  createTemplate: (data: { name: string; description?: string; category?: string; graphData: string }) =>
    request.post('/workflow/templates', data),
  cloneFromTemplate: (templateId: number) =>
    request.post<any>(`/workflow/templates/${templateId}/clone`),
  deleteTemplate: (templateId: number) =>
    request.delete(`/workflow/templates/${templateId}`),
```

- [ ] **Step 2: WorkflowListView.vue 添加模板功能**

在 `<script setup>` 中添加：

```typescript
const showTemplateModal = ref(false)
const templates = ref<any[]>([])

const fetchTemplates = async () => {
  try {
    const res = await workflowApi.listTemplates()
    templates.value = res.data
  } catch { templates.value = [] }
}

const handleCloneTemplate = async (templateId: number) => {
  const res = await workflowApi.cloneFromTemplate(templateId)
  showTemplateModal.value = false
  router.push(`/workflow/${res.data.id}`)
}

onMounted(() => { fetchList(); fetchTemplates() })
```

在页面顶部按钮区域，在 `+ 新建` 按钮后面添加：

```html
      <button class="btn btn-secondary" @click="showTemplateModal = true">
        从模板创建
      </button>
```

在现有的创建模态框 `</div>` 之后、`</div>` (最外层) 之前添加模板选择模态框：

```html
    <div v-if="showTemplateModal" class="modal-overlay" @click.self="showTemplateModal = false">
      <div class="modal-content">
        <h3>从模板创建</h3>
        <div class="template-list">
          <div v-for="tpl in templates" :key="tpl.id" class="template-card" @click="handleCloneTemplate(tpl.id)">
            <div class="tpl-head">
              <span class="tpl-name">{{ tpl.name }}</span>
              <span v-if="tpl.isSystem" class="tag tag-blue">系统</span>
            </div>
            <p class="tpl-desc">{{ tpl.description || '暂无描述' }}</p>
          </div>
          <div v-if="templates.length === 0" class="empty-state">暂无可用模板</div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showTemplateModal = false">取消</button>
        </div>
      </div>
    </div>
```

在 `<style>` 部分添加模板相关样式：

```scss
.template-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
.template-card {
  padding: 14px; border: 1px solid var(--border-subtle); border-radius: 8px;
  cursor: pointer; transition: border-color 0.2s;
  &:hover { border-color: #3b82f6; background: #eff6ff; }
}
.tpl-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.tpl-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.tpl-desc { font-size: 12px; color: var(--text-secondary); margin: 0; }
```

- [ ] **Step 3: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add aicoder-web/src/api/workflow.ts aicoder-web/src/views/workflow/WorkflowListView.vue
git commit -m "feat(web): 工作流列表页 — 从模板创建功能"
```

---

## Task 10: 后端 — 子工作流节点

**Files:**
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/node/SubWorkflowNode.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowExecutionService.java`

- [ ] **Step 1: 创建 SubWorkflowNode**

```java
package com.ai.coder.workflow.node;

import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.repository.WorkflowRepository;
import com.ai.coder.workflow.service.WorkflowExecutionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SubWorkflowNode implements NodeAction {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionService executionService;
    private final Long targetWorkflowId;
    private final String inputKey;
    private final String outputKey;

    public SubWorkflowNode(WorkflowRepository workflowRepository,
                           WorkflowExecutionService executionService,
                           Long targetWorkflowId, String inputKey, String outputKey) {
        this.workflowRepository = workflowRepository;
        this.executionService = executionService;
        this.targetWorkflowId = targetWorkflowId;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(inputKey, "").toString();
        log.info("SubWorkflowNode 执行: workflowId={}, inputLength={}", targetWorkflowId, input.length());

        Workflow subWorkflow = workflowRepository.findById(targetWorkflowId)
                .orElseThrow(() -> new IllegalArgumentException("子工作流不存在: " + targetWorkflowId));

        Map<String, Object> subInput = new HashMap<>();
        subInput.put("query", input);

        String result = executionService.executeSubWorkflow(subWorkflow, subInput);

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, result);
        return output;
    }
}
```

- [ ] **Step 2: WorkflowExecutionService 新增 executeSubWorkflow 方法**

在 `WorkflowExecutionService.java` 中添加公共方法，复用 `executeGraph` 但不记录到数据库和 SSE：

```java
public String executeSubWorkflow(Workflow workflow, Map<String, Object> input) throws Exception {
    JsonNode graphData = objectMapper.readTree(workflow.getGraphData());
    JsonNode nodes = graphData.path("nodes");
    JsonNode edges = graphData.path("edges");

    Set<String> allKeys = new HashSet<>();
    if (input != null) allKeys.addAll(input.keySet());
    for (JsonNode node : nodes) {
        JsonNode config = node.path("data").path("config");
        addIfPresent(allKeys, config, "inputKey");
        addIfPresent(allKeys, config, "outputKey");
    }
    allKeys.add("__route__");

    KeyStrategyFactory keyStrategyFactory = () -> {
        HashMap<String, KeyStrategy> strategies = new HashMap<>();
        for (String key : allKeys) {
            strategies.put(key, new ReplaceStrategy());
        }
        return strategies;
    };

    StateGraph stateGraph = new StateGraph(keyStrategyFactory);

    Map<String, String> nodeTypeMap = new HashMap<>();
    for (JsonNode node : nodes) {
        String nodeId = node.path("id").asText();
        String type = node.path("type").asText();
        nodeTypeMap.put(nodeId, type);
        if ("start".equals(type) || "end".equals(type)) continue;
        NodeAction nodeAction = nodeFactory.createNode(node);
        stateGraph.addNode(nodeId, AsyncNodeAction.node_async(state -> nodeAction.apply(state)));
    }

    String startNode = null;
    for (JsonNode edge : edges) {
        String source = edge.path("source").asText();
        String target = edge.path("target").asText();
        if ("start".equals(nodeTypeMap.get(source))) startNode = target;
    }
    if (startNode != null) stateGraph.addEdge(StateGraph.START, startNode);

    Map<String, Map<String, String>> conditionalEdgesMap = new LinkedHashMap<>();
    for (JsonNode edge : edges) {
        String source = edge.path("source").asText();
        String target = edge.path("target").asText();
        if ("start".equals(nodeTypeMap.get(source))) continue;
        boolean isTargetEnd = "end".equals(nodeTypeMap.get(target));
        String effectiveTarget = isTargetEnd ? StateGraph.END : target;
        if ("condition".equals(nodeTypeMap.get(source))) {
            String sourceHandle = edge.path("sourceHandle").asText("__default__");
            conditionalEdgesMap.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(sourceHandle, effectiveTarget);
        } else if (isTargetEnd) {
            stateGraph.addEdge(source, StateGraph.END);
        } else {
            stateGraph.addEdge(source, target);
        }
    }

    for (Map.Entry<String, Map<String, String>> entry : conditionalEdgesMap.entrySet()) {
        stateGraph.addConditionalEdges(entry.getKey(),
                com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async(state ->
                        state.value("__route__", "__default__").toString()),
                entry.getValue());
    }

    CompiledGraph compiledGraph = stateGraph.compile(CompileConfig.builder().build());
    NodeOutput lastOutput = compiledGraph.stream(input != null ? input : new HashMap<>()).blockLast();

    if (lastOutput != null) {
        return objectMapper.writeValueAsString(lastOutput.state().data());
    }
    return "{}";
}
```

注意：需要将 `executeGraph` 方法中的子工作流调用逻辑提取出来以避免递归中重复执行 SSE 和数据库操作。上面的 `executeSubWorkflow` 是简化版本，不记录执行日志也不发送 SSE。

- [ ] **Step 3: WorkflowNodeFactory 添加 subworkflow 分支**

在 `createNode` 方法的 switch 块中添加：

```java
case "subworkflow" -> createSubWorkflowNode(config);
```

在类中添加新的依赖注入字段（WorkflowExecutionService 已通过构造器注入）：

检查现有构造器参数，添加 `WorkflowRepository` 和 `WorkflowExecutionService`（如果还没有）。

在 `createToolNode` 方法后添加：

```java
private SubWorkflowNode createSubWorkflowNode(JsonNode config) {
    Long workflowId = config.path("workflowId").asLong();
    String inputKey = config.path("inputKey").asText("query");
    String outputKey = config.path("outputKey").asText("subResult");
    return new SubWorkflowNode(workflowRepository, executionService, workflowId, inputKey, outputKey);
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/node/SubWorkflowNode.java aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowExecutionService.java
git commit -m "feat(workflow): 子工作流节点 SubWorkflowNode"
```

---

## Task 11: 后端 — 循环节点

**Files:**
- Create: `aicoder-workflow/src/main/java/com/ai/coder/workflow/node/LoopNode.java`
- Modify: `aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java`

- [ ] **Step 1: 创建 LoopNode**

LoopNode 是一个简单的迭代控制节点：每次迭代它透传 state 数据，检查退出条件（字符串匹配）。实际循环由图中的回路边驱动——LoopNode 只在每次被调用时检查是否满足退出条件或达到最大迭代次数。

```java
package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LoopNode implements NodeAction {

    private final int maxIterations;
    private final String exitCondition;
    private final String inputKey;
    private final String outputKey;
    private final String iterationKey;

    public LoopNode(int maxIterations, String exitCondition, String inputKey, String outputKey) {
        this.maxIterations = maxIterations;
        this.exitCondition = exitCondition;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
        this.iterationKey = "__loop_iteration__";
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String value = state.value(inputKey, "").toString();
        int iteration = 0;
        if (state.value(iterationKey, null) != null) {
            iteration = Integer.parseInt(state.value(iterationKey, "0").toString());
        }
        iteration++;

        boolean shouldExit = false;
        if (exitCondition != null && !exitCondition.isEmpty() && value.contains(exitCondition)) {
            shouldExit = true;
        }
        if (iteration >= maxIterations) {
            shouldExit = true;
        }

        log.info("LoopNode 迭代 {}/{}, shouldExit={}, valueLength={}",
                iteration, maxIterations, shouldExit, value.length());

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, value);
        output.put(iterationKey, iteration);

        if (shouldExit) {
            output.put("__route__", "__default__");
        }

        return output;
    }
}
```

- [ ] **Step 2: WorkflowNodeFactory 添加 loop 分支**

在 switch 块中添加：

```java
case "loop" -> createLoopNode(config);
```

在 `createSubWorkflowNode` 方法后添加：

```java
private LoopNode createLoopNode(JsonNode config) {
    int maxIterations = config.path("maxIterations").asInt(5);
    String exitCondition = config.path("exitCondition").asText("");
    String inputKey = config.path("inputKey").asText("query");
    String outputKey = config.path("outputKey").asText("loopResult");
    return new LoopNode(maxIterations, exitCondition, inputKey, outputKey);
}
```

- [ ] **Step 3: WorkflowExecutionService 注册 __loop_iteration__ 键**

在 `executeGraph` 方法中 `allKeys.add("__route__");` 后添加：

```java
allKeys.add("__loop_iteration__");
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-workflow -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add aicoder-workflow/src/main/java/com/ai/coder/workflow/node/LoopNode.java aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowNodeFactory.java aicoder-workflow/src/main/java/com/ai/coder/workflow/service/WorkflowExecutionService.java
git commit -m "feat(workflow): 循环节点 LoopNode"
```

---

## Task 12: 前端 — 类型扩展 + 新节点画布组件

**Files:**
- Modify: `aicoder-web/src/types/workflow.ts`
- Create: `aicoder-web/src/views/workflow/components/nodes/SubWorkflowNode.vue`
- Create: `aicoder-web/src/views/workflow/components/nodes/LoopNode.vue`

- [ ] **Step 1: 扩展 WorkflowNodeConfig**

在 `workflow.ts` 的 `WorkflowNodeConfig` 接口中添加：

```typescript
  // SubWorkflow
  workflowId?: string
  // Loop
  maxIterations?: number
  exitCondition?: string
```

- [ ] **Step 2: 创建 SubWorkflowNode.vue**

```vue
<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
</script>

<template>
  <div class="workflow-node subworkflow-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge sub">子流</span>
      <span class="node-title">{{ props.data?.label || '子工作流' }}</span>
    </div>
    <div class="node-info">
      <span v-if="props.data?.config?.workflowId" class="node-tag">WF #{{ props.data.config.workflowId }}</span>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped lang="scss">
.subworkflow-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 160px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.sub { background: #ede9fe; color: #6d28d9; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
}
</style>
```

- [ ] **Step 3: 创建 LoopNode.vue**

```vue
<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
</script>

<template>
  <div class="workflow-node loop-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge loop">循环</span>
      <span class="node-title">{{ props.data?.label || '循环' }}</span>
    </div>
    <div class="node-info">
      <span class="node-tag">最多 {{ props.data?.config?.maxIterations || 5 }} 次</span>
      <span v-if="props.data?.config?.exitCondition" class="node-tag">退出: {{ props.data.config.exitCondition }}</span>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped lang="scss">
.loop-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 160px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.loop { background: #cffafe; color: #0e7490; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
}
</style>
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 5: Commit**

```bash
git add aicoder-web/src/types/workflow.ts aicoder-web/src/views/workflow/components/nodes/SubWorkflowNode.vue aicoder-web/src/views/workflow/components/nodes/LoopNode.vue
git commit -m "feat(web): 子工作流/循环节点画布组件 + 类型扩展"
```

---

## Task 13: 前端 — FlowCanvas + NodePanel 注册新节点

**Files:**
- Modify: `aicoder-web/src/views/workflow/components/FlowCanvas.vue`
- Modify: `aicoder-web/src/views/workflow/components/NodePanel.vue`

- [ ] **Step 1: FlowCanvas.vue 注册新节点**

在 import 区域添加：

```typescript
import SubWorkflowNode from './nodes/SubWorkflowNode.vue'
import LoopNode from './nodes/LoopNode.vue'
```

在 `nodeTypes` 对象中添加：

```typescript
  subworkflow: SubWorkflowNode,
  loop: LoopNode,
```

在 `onDrop` 的 `labels` 映射中添加：

```typescript
  subworkflow: '子工作流',
  loop: '循环',
```

- [ ] **Step 2: NodePanel.vue 添加新拖拽项**

在 `nodeItems` 数组末尾添加：

```typescript
  {
    type: 'subworkflow',
    label: '子工作流',
    badge: '子流',
    badgeClass: 'sub',
    defaultConfig: { workflowId: '', inputKey: 'query', outputKey: 'subResult' }
  },
  {
    type: 'loop',
    label: '循环',
    badge: '循环',
    badgeClass: 'loop',
    defaultConfig: { maxIterations: 5, exitCondition: '', inputKey: 'query', outputKey: 'loopResult' }
  }
```

在 `.badge` 样式中添加：

```scss
  &.sub { background: #ede9fe; color: #6d28d9; }
  &.loop { background: #cffafe; color: #0e7490; }
```

- [ ] **Step 3: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add aicoder-web/src/views/workflow/components/FlowCanvas.vue aicoder-web/src/views/workflow/components/NodePanel.vue
git commit -m "feat(web): 画布和面板注册子工作流/循环节点"
```

---

## Task 14: 前端 — ConfigPanel 新节点配置表单

**Files:**
- Modify: `aicoder-web/src/views/workflow/components/ConfigPanel.vue`

- [ ] **Step 1: 添加 subworkflow 配置表单**

在 `</template>`（tool 配置的闭合标签）之后、`<button class="btn-delete"` 之前添加：

```html
      <template v-if="node.type === 'subworkflow'">
        <div class="form-group">
          <label>目标工作流 ID</label>
          <input :value="node.data.config.workflowId" @input="updateConfig('workflowId', ($event.target as HTMLInputElement).value)" placeholder="输入工作流 ID" />
          <p class="hint">填入已创建的工作流 ID，执行时将调用该工作流</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'loop'">
        <div class="form-group">
          <label>最大迭代次数</label>
          <input type="number" :value="node.data.config.maxIterations" @input="updateConfig('maxIterations', parseInt(($event.target as HTMLInputElement).value) || 5)" />
        </div>
        <div class="form-group">
          <label>退出条件</label>
          <input :value="node.data.config.exitCondition" @input="updateConfig('exitCondition', ($event.target as HTMLInputElement).value)" placeholder="包含此文本时退出" />
          <p class="hint">当输入值包含指定文本时停止循环</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add aicoder-web/src/views/workflow/components/ConfigPanel.vue
git commit -m "feat(web): 配置面板支持子工作流/循环节点"
```

---

## Task 15: 前端 — Chat 流式调用统一

**Files:**
- Modify: `aicoder-web/src/views/chat/ChatView.vue`

- [ ] **Step 1: ChatView.vue 使用 streamRequest**

在 `ChatView.vue` 的 import 区域添加：

```typescript
import { streamRequest } from '@/api/request'
```

将 `streamChat` 函数中手动 fetch + ReadableStream 解析逻辑替换为：

```typescript
const streamChat = async (message: string) => {
  try {
    await streamRequest('/api/chat/stream', {
      model: chatStore.currentModel,
      message: message,
      conversationId: chatStore.currentConversationId
    }, {
      onEvent(eventType, data) {
        if (eventType === 'conversation') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.conversationId && !chatStore.currentConversationId) {
              chatStore.currentConversationId = parsed.conversationId
            }
          } catch { /* ignore */ }
        } else if (data && data !== '[DONE]') {
          chatStore.appendStreamContent(data)
        }
      },
      onError() {
        chatStore.finishStreaming()
      },
      onComplete() {
        chatStore.finishStreaming()
        chatStore.fetchConversations()
      }
    })
  } catch {
    chatStore.finishStreaming()
  }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add aicoder-web/src/views/chat/ChatView.vue
git commit -m "refactor(web): Chat 流式调用统一使用 streamRequest"
```

---

## Task 16: 最终验证

- [ ] **Step 1: 后端全量编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 前端全量编译**

Run: `cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npm run build`
Expected: 编译成功

- [ ] **Step 3: 最终 Commit**

如果有未提交的变更：

```bash
git add -A
git commit -m "feat: 第三期批次 A 完成 — 补齐遗留 + 仪表盘 + 工作流模板 + 子工作流/循环"
```
