---
title: 模型注册表热重载
description: admin 修改模型配置后自动通知 chat/rag/workflow 三服务重载内存模型表，实现启用即生效免重启
status: active
---

## Purpose

`DynamicModelRegistry` 是 `@PostConstruct` 启动时一次性把模型读进内存。admin 新增/启停模型后，运行中的 chat/rag/workflow 不感知，必须重启对应服务才生效。本 spec 给注册表加热重载：admin 改完模型配置自动异步通知三服务重载内存表。

## Requirements

### Requirement: The system SHALL provide an internal reload endpoint on each service
aicoder-core SHALL 新增 `RegistryReloadController`（`@RestController @RequestMapping("/internal/registry")`），注入 `AbstractDynamicModelRegistry`，暴露 `POST /reload`，调用 `registry.reload()` 返回 200。

#### Scenario: 重载成功
- **WHEN** admin 或其他调用方 POST `http://aicoder-chat/internal/registry/reload`
- **THEN** 服务重读 DB 模型配置，构建新 HashMap，原子替换 `chatModels` 引用，重跑探针与 `registerExtra` 钩子，返回 HTTP 200

#### Scenario: 端点不在 Gateway 路由范围内
- **WHEN** 外部请求经 Gateway 访问 `/internal/registry/reload`
- **THEN** Gateway 不路由该路径（仅 `/api/**` 路由），外部不可达

### Requirement: The system SHALL make reload atomic and concurrent-safe
`reload()` SHALL 先构建局部新 `HashMap`（全部模型建好+探针+`registerExtra`），然后用 `this.chatModels = newMap` 原子替换，`chatModels` 字段 SHALL 标记 `volatile`，方法体 SHALL 加 `synchronized` 防并发 reload 互相覆盖。

#### Scenario: 并发读安全
- **WHEN** reload 过程中有 `getChatModel` 调用
- **THEN** 调用拿到的是旧表或新表的完整引用（原子替换），不会读到半构建的 map

#### Scenario: 并发 reload 串行化
- **WHEN** 两个请求同时触发 reload
- **THEN** `synchronized` 保证先后执行，后者覆盖前者，最终状态为最后一次重载结果

### Requirement: The system SHALL trigger reload on ModelConfig write operations
admin 的 `ModelConfigController` 在 create/update（含 enabled 启停）/delete 持久化成功后 SHALL 调 `RegistryReloadNotifier.reloadAll()` 异步通知三服务重载。

#### Scenario: 新增模型即时可用
- **WHEN** admin 保存一个新 enabled 模型配置
- **THEN** `reloadAll()` 通知 chat/rag/workflow 各 `POST /internal/registry/reload`，新模型即时可用无需重启

#### Scenario: 启停模型即时生效
- **WHEN** admin 将某模型 enabled 改为 false
- **THEN** 重载后该模型从 `chatModels` 中移除，后续 `getChatModel` 不再返回该模型

#### Scenario: 删除模型即时生效
- **WHEN** admin 删除某模型配置
- **THEN** 重载后该模型从 `chatModels` 中移除

### Requirement: The system SHALL use fire-and-forget async notification
`RegistryReloadNotifier.reloadAll()` SHALL 使用 `@Async` 异步执行，对 chat/rag/workflow 各发一次 `POST http://aicoder-<svc>/internal/registry/reload`，通过 `@LoadBalanced RestTemplate` 经 Nacos LB 直连服务（短超时：连接 2s/读 3s）。

#### Scenario: 通知成功
- **WHEN** 三服务均可达
- **THEN** 三服务均收到 reload POST 并重载内存表

#### Scenario: 部分服务不可达
- **WHEN** 某服务挂了或网络不通
- **THEN** notifier 捕获异常并 `log.warn`，继续通知其余服务，不传播异常，admin 保存仍返回成功

#### Scenario: admin 保存不阻塞
- **WHEN** admin 保存模型配置
- **THEN** 保存结果秒回，reload 通知异步执行不阻塞响应

### Requirement: The system SHALL add @ComponentScan for shared controller in each app
chat/rag/workflow 的 app 类 SHALL 添加 `@ComponentScan(basePackages = {"com.ai.coder.<module>", "com.ai.coder.core"})`，使 core 的 `RegistryReloadController` 被扫描注册。

#### Scenario: controller 注册生效
- **WHEN** 各服务启动
- **THEN** `POST /internal/registry/reload` 端点可用，无需每模块写重复 controller

### Requirement: The system SHALL add loadbalancer starter to admin
admin 的 pom SHALL 添加 `spring-cloud-starter-loadbalancer`，使 `@LoadBalanced RestTemplate` 能通过 Nacos 解析服务名。

#### Scenario: 服务名解析
- **WHEN** admin 通过 `http://aicoder-chat/internal/registry/reload` 发请求
- **THEN** RestTemplate 经 Nacos LB 解析 `aicoder-chat` 为实际 IP:Port 并成功调用
