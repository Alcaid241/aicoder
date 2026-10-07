---
title: 模型管理 — 新增 support_tools 字段（delta）
description: model-management spec 的变更：新增模型配置字段和前端开关
status: active
---

## Purpose

Delta spec：对 `openspec/specs/model-management/spec.md` 的变更。新增 `support_tools` 字段到模型配置的 CRUD 接口和前端表单。

## MODIFIED Requirements

### Requirement: The system SHALL manage model configurations with model type support

在新增模型配置和编辑模型配置时，SHALL 增加 `support_tools` 字段（TINYINT，0/1）。

#### Scenario: 新增模型配置（追加 support_tools 字段）
- **WHEN** 管理员 POST `/api/admin/model/config` 携带 `providerId`、`displayName`、`modelCode`、`modelType`、`supportTools`（可选，默认 0）
- **THEN** 创建 ai_model_config 记录，含 support_tools 字段，关联到指定厂商

### Requirement: The system SHALL provide frontend pages for provider and model configuration

模型配置表单 SHALL 增加「支持 Function Calling」开关。

#### Scenario: 模型配置表单新增字段
- **WHEN** 管理员新增或编辑模型配置
- **THEN** 弹窗中显示「支持 Function Calling」switch 组件，保存时携带 supportTools 字段到后端 API
