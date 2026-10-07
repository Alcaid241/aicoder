---
title: DynamicModelRegistry 去重 — 抽取 aicoder-core 共享模块
description: 将 chat/rag/workflow 三模块重复的 ModelConfig/ModelProvider 实体、Repository、DynamicModelRegistry 逻辑抽取为编译期共享模块
status: active
---

## Purpose

chat/rag/workflow 三模块各有一份近乎相同的 `DynamicModelRegistry`（~90% 重复），且各自的 `ModelConfig`/`ModelProvider` 实体与 Repository 也 x3 重复。本 spec 新建 `aicoder-core` 编译期共享模块（jar），把共享逻辑抽到一处，改一次生效三处，不引入运行时中心化耦合。

## Requirements

### Requirement: The system SHALL provide aicoder-core as a compile-time shared module
`aicoder-core` SHALL 包含共享实体 `ModelConfig`/`ModelProvider`、对应 Repository、以及基类 `AbstractDynamicModelRegistry`，作为编译期 jar 被 chat/rag/workflow 依赖，保留每个服务在自己 JVM 内 `@PostConstruct` 构建内存模型的能力。

#### Scenario: 模块依赖编译通过
- **WHEN** chat/rag/workflow 的 pom 添加 `aicoder-core` 依赖并删除本地实体/Repository 副本
- **THEN** 全量 `mvn clean compile` 通过，无 "Not a managed type" 错误

### Requirement: The system SHALL provide AbstractDynamicModelRegistry as shared base class
基类 SHALL 实现 `@PostConstruct init()` 遍历 enabled providers+models，构建 `chatModels` HashMap，包含 `createChatModel`（switch OLLAMA/DEEPSEEK）、`probeOllama` 启动探针、`getChatModel`/`getChatModelMap` 方法，并提供空的 `registerExtra` 钩子供子类覆盖。

#### Scenario: 启动注册日志一致
- **WHEN** 各服务启动
- **THEN** "注册 Chat 模型" 日志与重构前完全一致，启动探针对 OLLAMA 模型照常执行

#### Scenario: 探针复用
- **WHEN** 配置中存在不可达的 Ollama 模型
- **THEN** 基类 `probeOllama` 输出 WARN 日志（"模型在 baseUrl 不可达"），行为与重构前一致

### Requirement: The system SHALL let chat subclass add getAvailableChatModels only
chat 的 `DynamicModelRegistry` extends `AbstractDynamicModelRegistry` SHALL 仅添加 `getAvailableChatModels()` 方法（读 DB 返回 `ModelInfoDTO` 列表），不覆写任何基类方法。

#### Scenario: chat 前端模型列表可用
- **WHEN** 前端请求可用模型列表
- **THEN** `getAvailableChatModels()` 返回 enabled CHAT 模型及信息

### Requirement: The system SHALL let rag/workflow subclasses add embedding model support
rag 和 workflow 的子类 SHALL 通过覆盖 `registerExtra` 钩子构建 `OllamaEmbeddingModel`（取首个 enabled EMBEDDING 模型），并提供 `getEmbeddingModel()` 方法。

#### Scenario: rag embedding 模型注册
- **WHEN** rag 服务启动且配置了 enabled EMBEDDING 模型
- **THEN** "注册 Embedding 模型" 日志出现，`getEmbeddingModel()` 返回可用实例

#### Scenario: embedding 模型缺失
- **WHEN** 无 enabled EMBEDDING 模型
- **THEN** `getEmbeddingModel()` 抛出 IllegalStateException

### Requirement: The system SHALL configure JPA entity scanning for the shared module
每个 app 类（chat/rag/workflow）SHALL 添加 `@EntityScan` 和 `@EnableJpaRepositories` 包含 `com.ai.coder.core` 包，使 shared 模块的实体和 Repository 可被 JPA 管理。

#### Scenario: JPA 扫描生效
- **WHEN** 各服务启动
- **THEN** `ModelConfigRepository` 和 `ModelProviderRepository` 可正常注入，无 "Not a managed type" 启动报错

### Requirement: The system SHALL delete local duplicate entities and repositories
迁移完成后 chat/rag/workflow 各自的 `ModelConfig.java`、`ModelProvider.java`、`ModelConfigRepository.java`、`ModelProviderRepository.java` SHALL 被删除，不保留本地副本，避免 bean 冲突。

#### Scenario: 无 bean 冲突
- **WHEN** 各服务启动且已删除本地实体/Repository
- **THEN** Spring 容器中仅 common 模块的 Repository bean，无冲突报错
