## 实现任务

### Task 1: DB 加 support_tools 列

**Files:**
- Modify: `sql/schema.sql` — 加 `ALTER TABLE ai_model_config ADD COLUMN support_tools TINYINT NOT NULL DEFAULT 0;`
- Execute: 在本地 test_ai 数据库执行该 SQL；存量 DeepSeek 模型手动 `UPDATE ai_model_config SET support_tools=1 WHERE provider_id=2;`

- [ ] Step 1: 在 schema.sql 末尾加 `ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS support_tools TINYINT NOT NULL DEFAULT 0;`
- [ ] Step 2: 在本地 MySQL 执行 SQL
- [ ] Step 3: `UPDATE ai_model_config SET support_tools=1 WHERE provider_id=2;`
- [ ] Step 4: Commit

### Task 2: aicoder-core ModelConfig 实体加字段

**Files:**
- Modify: `aicoder-core/src/main/java/com/ai/coder/core/entity/ModelConfig.java`

- [ ] Step 1: 在 entity 加 `@Column(nullable = false) private Integer supportTools = 0;`
- [ ] Step 2: 编译 `mvn clean compile -pl aicoder-core -am -DskipTests` → BUILD SUCCESS
- [ ] Step 3: Commit

### Task 3: aicoder-admin CRUD 适配 support_tools

**Files:**
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/CreateModelConfigRequest.java`
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/dto/UpdateModelConfigRequest.java`
- Modify: `aicoder-admin/src/main/java/com/ai/coder/admin/init/ModelDataInitializer.java`

- [ ] Step 1: CreateModelConfigRequest 加 `private Integer supportTools = 0;`
- [ ] Step 2: UpdateModelConfigRequest 加 `private Integer supportTools = 0;`
- [ ] Step 3: ModelDataInitializer 中 DeepSeek 模型初始化代码加 `.supportTools(1)` 调用
- [ ] Step 4: 编译 `mvn clean compile -pl aicoder-admin -am -DskipTests` → BUILD SUCCESS
- [ ] Step 5: Commit

### Task 4: 前端模型配置表单加 switch

**Files:**
- Modify: `aicoder-web/src/views/model/config/ModelConfigView.vue`
- Modify: `aicoder-web/src/api/model.ts`

- [ ] Step 1: model.ts 的 ModelConfig 类型和 API 请求加 `supportTools: number`
- [ ] Step 2: ModelConfigView.vue 表单加 el-switch 组件，绑定 `form.supportTools`
- [ ] Step 3: 编译前端 `npm run build` → 无错误
- [ ] Step 4: Commit

### Task 5: ChatService 改读 support_tools

**Files:**
- Modify: `aicoder-chat/src/main/java/com/ai/coder/chat/service/ChatService.java`

- [ ] Step 1: 注入 `ModelConfigRepository`
- [ ] Step 2: buildSkillClient 中把 `instanceof OllamaChatModel` 改为查 ModelConfig.supportTools
- [ ] Step 3: 编译 + 运行测试 `mvn test -pl aicoder-chat` → Tests pass
- [ ] Step 4: Commit

### Task 6: 端到端验证

- [ ] Step 1: 重启 admin + chat
- [ ] Step 2: 前端模型配置页为 glm-4.7-flash 开启「支持 Function Calling」开关
- [ ] Step 3: 对话模式选 glm-4.7-flash 问"有哪些技能" → Agent 能调 read_skill 返回技能列表
- [ ] Step 4: 关闭开关后对话不再召回技能工具
