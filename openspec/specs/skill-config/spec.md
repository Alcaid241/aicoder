---
title: Agent Skill 管理面配置与错误码规范化
description: 将 skill.directory 提升为 Nacos 共享配置实现跨服务目录一致性，并用领域异常重建错误码语义（404/409/500）。
status: active
---

## Purpose

解决 Phase 1 管理面验证发现的两个问题：技能共享目录路径因 JVM 工作目录不同而出现不一致（cwd 混淆），以及 GlobalExceptionHandler 将所有 RuntimeException 宽泛映射为 400 导致错误码语义不准。

## Requirements

### Requirement: The system SHALL resolve skill.directory from Nacos shared configuration as the cross-service source of truth

The system SHALL read skill.directory from Nacos data-id aicoder-shared.yml via spring.config.import. 技能共享目录路径通过 Nacos 统一管理，所有服务（skill、chat）读取同一配置值，避免因各服务 JVM 工作目录不同导致物化与读取路径不一致。

#### Scenario: Nacos 配置可用时使用远程值
- **WHEN** Nacos 服务可用且 data-id `aicoder-shared.yml` 中存在 `skill.directory` 配置
- **THEN** 服务启动时通过 `spring.config.import: optional:nacos:aicoder-shared.yml` 加载该值，`SkillProperties` 解析为绝对路径并在日志中以 WARN 级别打印来源为 nacos

#### Scenario: Nacos 不可用时回退本地默认值
- **WHEN** Nacos 服务不可用或 `aicoder-shared.yml` 不存在
- **THEN** `optional:` 前缀保证服务仍可启动，`SkillProperties` 回退到本地 `application.yml` 中的 `skill.directory` 默认值（支持 `${SKILL_DIRECTORY}` 环境变量覆盖），日志标注来源为 env 或 default

#### Scenario: 相对路径解析为绝对路径
- **WHEN** `skill.directory` 配置为相对路径如 `./skills`
- **THEN** `SkillProperties` 在 `@PostConstruct` 阶段通过 `File.getAbsoluteFile()` 解析为绝对路径并以 WARN 级别打印，使 cwd 混淆在启动日志中可见

### Requirement: The system SHALL support dynamic refresh of skill.directory via Nacos

The system SHALL apply @RefreshScope to SkillProperties for runtime config refresh. 当 Nacos 中 `skill.directory` 值变更时，运行时自动刷新该值。

#### Scenario: Nacos 配置变更后新技能使用新路径
- **WHEN** Nacos 中 `aicoder-shared.yml` 的 `skill.directory` 被修改
- **THEN** `SkillProperties` 通过 `@RefreshScope` 自动更新，后续审批激活的新技能物化到新路径

#### Scenario: 刷新不迁移已物化技能
- **WHEN** `skill.directory` 变更后
- **THEN** 已 ACTIVE 技能的 SKILL.md 文件停留在旧路径不移除不迁移（已知限制，留待后续按需处理）

### Requirement: The system SHALL map domain exceptions to precise HTTP status codes

The system SHALL use domain exceptions (SkillNotFoundException/DuplicateSkillException/IllegalSkillStateException) instead of generic RuntimeException. GlobalExceptionHandler 精确映射为 404 / 409 / 500，不再将所有未知异常报为 400。

#### Scenario: 查询不存在的技能返回 404
- **WHEN** 通过 ID 查询技能但数据库中不存在该记录
- **THEN** `SkillService.getById` 抛出 `SkillNotFoundException`，`GlobalExceptionHandler` 映射为 HTTP 404，响应体格式为 `{"code": 404, "message": "技能不存在：xxx"}`

#### Scenario: 创建重名技能返回 409
- **WHEN** 创建技能时 name 与已有技能冲突
- **THEN** `SkillService.create` 抛出 `DuplicateSkillException`，`GlobalExceptionHandler` 映射为 HTTP 409，响应体格式为 `{"code": 409, "message": "技能名已存在：xxx"}`

#### Scenario: 非法状态迁移返回 409
- **WHEN** 对当前状态不允许的操作（如 approve 一个 ARCHIVED 技能）
- **THEN** `SkillLifecycleService.assertTransition` 抛出 `IllegalSkillStateException`，`GlobalExceptionHandler` 映射为 HTTP 409

#### Scenario: 意外异常返回 500
- **WHEN** 发生 NPE 等未预期的运行时异常
- **THEN** `GlobalExceptionHandler` 的兜底 `@ExceptionHandler(Exception.class)` 映射为 HTTP 500，不再误报为 400

### Requirement: The system SHALL use a unified error response body format

The system SHALL return all errors in the format {"code": <HTTP_STATUS>, "message": "<description>"}. 统一错误响应体格式，与 gateway 的 401 体格式保持一致。

#### Scenario: 404 错误体格式
- **WHEN** 触发 SkillNotFoundException
- **THEN** 响应体为 `{"code": 404, "message": "技能不存在：<id>"}`

#### Scenario: 409 错误体格式
- **WHEN** 触发 DuplicateSkillException 或 IllegalSkillStateException
- **THEN** 响应体为 `{"code": 409, "message": "技能名已存在：<name>"}` 或对应消息
