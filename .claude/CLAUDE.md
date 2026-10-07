# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

### 后端 (Maven)

```bash
# 编译全部模块
mvn clean compile -DskipTests

# 编译并打包（跳过测试）
mvn clean package -DskipTests

# 运行全部测试
mvn test

# 运行单个模块的测试
mvn test -pl aicoder-rag

# 启动单个服务（开发模式）
mvn spring-boot:run -pl aicoder-gateway
mvn spring-boot:run -pl aicoder-admin
mvn spring-boot:run -pl aicoder-system
mvn spring-boot:run -pl aicoder-chat
mvn spring-boot:run -pl aicoder-rag
mvn spring-boot:run -pl aicoder-workflow
```

### 前端 (Vite + Vue)

```bash
cd aicoder-web
npm install                    # 安装依赖
npm run dev                    # 启动开发服务器 (localhost:8888)
npm run build                  # 生产构建
npm run preview                # 预览生产构建
```

### 基础中间件（通过 Docker 或本地服务启动）

```bash
# MySQL (端口 3306, 数据库 test_ai, 用户 root/root)
# Redis (端口 6379, 密码 ${REDIS_PASSWORD})
# Nacos (端口 8848)
```

首次启动前，用 `sql/schema.sql` 初始化数据库表结构。

## 架构概览

### 微服务路由 (Spring Cloud Gateway → Nacos)

```
浏览器 (Vite :8888) → Gateway (:8080) → Nacos 服务发现 → 各微服务
                         │
                         ├── /api/admin/**   → aicoder-admin   (:8081) — 认证、模型/向量库配置
                         ├── /api/chat/**    → aicoder-chat    (:8082) — 多模型对话、流式、Agent
                         ├── /api/rag/**     → aicoder-rag     (:8083) — 知识库RAG、NL2SQL
                         ├── /api/workflow/**→ aicoder-workflow (:8084) — AI工作流编排
                         └── /api/system/**  → aicoder-system  (:8085) — RBAC用户/角色/菜单
```

Vite 开发服务器将所有 `/api/*` 请求代理到 `localhost:8080`（Gateway）。

### 启动顺序

1. **MySQL、Redis、Nacos** — 基础中间件
2. **aicoder-gateway** (:8080) — API 网关（最先启动，WebFlux 非阻塞）
3. **aicoder-system** (:8085) — RBAC 基础数据初始化
4. **aicoder-admin** (:8081) — 认证服务 + 模型配置初始化
5. **aicoder-chat** (:8082)、**aicoder-rag** (:8083)、**aicoder-workflow** (:8084) — 业务服务（无严格顺序依赖）
6. **aicoder-web** (:8888) — 前端开发服务器

### JWT 认证流程

1. `aicoder-admin` 的 `JwtUtil` 生成 Token（HMAC256，含 userId、username，24h 过期）
2. 前端 `request.ts` 拦截器在 Authorization 头附加 `Bearer <token>`
3. Gateway `JwtAuthFilter`（GlobalFilter）校验 Token，提取 `userId`/`username` 写入 `X-User-Id` / `X-Username` 请求头转发给下游服务
4. 白名单路径（`/api/admin/login`、`/api/admin/register`、Swagger 文档）跳过校验

### 动态模型注册表 (DynamicModelRegistry)

`aicoder-chat`、`aicoder-rag`、`aicoder-workflow` 各自维护 `DynamicModelRegistry`，监听 Nacos 配置变更实时切换模型，支持 DeepSeek API 和 Ollama 本地模型。

### 流式对话 (SSE)

Chat 和 RAG 的流式接口使用 Spring WebFlux `SseEmitter` / `Flux<ServerSentEvent>`。前端 `streamRequest()` 使用 Fetch API 手动解析 SSE 事件流。

### 数据库

- **无 Flyway/Liquibase** — 初始 DDL 在 `sql/schema.sql`，运行时 Hibernate `ddl-auto: update` 自动更新
- `aicoder-system` 的 `DataInitializer` 在启动时自动插入默认角色、菜单和管理员用户
- `aicoder-admin` 的 `ModelDataInitializer` 在启动时自动插入默认模型提供商和配置

### Spring AI 版本注意事项

项目使用 Spring AI 1.1.2 和 Spring AI Alibaba 1.1.2.0/1.1.2.1。Workflow 模块额外使用 `spring-ai-alibaba-graph-core` 1.1.2.2 进行工作流图编排。

## 模块职责

| 模块 | 职责 |
|------|------|
| **aicoder-gateway** | API 网关：JWT 验证、CORS、路由转发、请求日志 |
| **aicoder-admin** | 认证服务：登录/注册、JWT 签发、模型/Provider/向量库配置管理 |
| **aicoder-system** | RBAC：用户、角色、权限、菜单树管理 |
| **aicoder-chat** | 对话服务：多模型对话（流式+非流式）、Agent 工具调用、会话管理 |
| **aicoder-rag** | RAG 服务：知识库管理、文档解析（Tika）、向量检索增强生成、NL2SQL |
| **aicoder-workflow** | 工作流服务：可视化 AI Agent 工作流编排、执行引擎、节点（LLM/RAG/NL2SQL/条件/循环/工具/子工作流） |
| **aicoder-web** | Vue 3 前端：SPA 单页应用、hash 路由、Pinia 状态管理 |

## 设计文档

- **OpenSpec specs**：`openspec/specs/` — 各功能模块的结构化规范（Purpose + Requirements + Scenarios），可通过 `openspec list --specs` / `openspec validate --specs` 查看和校验。
- **详细设计文档**：`docs/superpowers/specs/` — 各功能的详细设计规格（含架构、组件、数据流等完整上下文）。
- **实现计划**：`docs/superpowers/plans/` — 对应的实现计划（已完成的历史参考）。未来新功能用 OpenSpec 工作流（`/opsx:propose` → `/opsx:apply` → `/opsx:archive`）管理。
- **Memory**：`~/.claude/projects/<project>/memory/` — 跨会话的项目知识（非代码的坑/诀窍），通过 MEMORY.md 索引。
