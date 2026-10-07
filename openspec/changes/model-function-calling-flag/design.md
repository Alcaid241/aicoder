## 设计

### 1. 数据库变更

```sql
ALTER TABLE ai_model_config ADD COLUMN support_tools TINYINT NOT NULL DEFAULT 0;
```

存量数据 migration：已有 DeepSeek 模型手动更新为 1。

### 2. Entity 变更（aicoder-core）

`ModelConfig.java` 新增字段：

```java
@Column(nullable = false)
private Integer supportTools = 0;
```

### 3. Admin 后端（aicoder-admin）

**CreateModelConfigRequest / UpdateModelConfigRequest DTO**：新增 `supportTools` 字段（Integer，默认 0）。

**ModelDataInitializer**：初始化 DeepSeek 模型时设 `supportTools=1`，Ollama 模型设 0。

### 4. Admin 前端（aicoder-web）

模型配置表单弹窗增加 el-switch：

```html
<el-form-item label="支持 Function Calling">
  <el-switch v-model="form.supportTools" :active-value="1" :inactive-value="0" />
</el-form-item>
```

### 5. ChatService 改动（aicoder-chat）

```java
// 之前
if (!(chatModel instanceof OllamaChatModel)) {
    builder.defaultTools(readSkillTool, submitSkillDraftTool);
}

// 之后
ModelConfig config = modelConfigRepository.findByModelCodeAndEnabled(modelId, 1);
if (config != null && config.getSupportTools() != null && config.getSupportTools() == 1) {
    builder.defaultTools(readSkillTool, submitSkillDraftTool);
}
```

ChatService 注入 `ModelConfigRepository`（已在 aicoder-core 中），按 modelCode 查 support_tools 标记。

### 6. 文件清单

| 模块 | 文件 | 操作 |
|------|------|------|
| aicoder-core | `entity/ModelConfig.java` | 改：加 supportTools 字段 |
| aicoder-admin | `dto/CreateModelConfigRequest.java` | 改：加 supportTools 字段 |
| aicoder-admin | `dto/UpdateModelConfigRequest.java` | 改：加 supportTools 字段 |
| aicoder-admin | `service/ModelConfigService.java` | 改：create/update 保存 supportTools |
| aicoder-admin | `init/ModelDataInitializer.java` | 改：DeepSeek 模型设 supportTools=1 |
| aicoder-chat | `service/ChatService.java` | 改：读 support_tools 替代 instanceof |
| aicoder-web | `views/model/config/ModelConfigView.vue` | 改：表单加 switch |
| aicoder-web | `api/model.ts` | 改：API 类型加 supportTools |
| sql | `schema.sql` | 改：加 support_tools 列 |
