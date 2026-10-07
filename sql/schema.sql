-- AI Coder 数据库建表脚本
-- 数据库: test_ai

-- 1. 用户表
CREATE TABLE IF NOT EXISTS ai_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密',
    nickname VARCHAR(100),
    email VARCHAR(100),
    avatar VARCHAR(500),
    status TINYINT DEFAULT 1 COMMENT '1-正常 0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 向量数据库配置表
CREATE TABLE IF NOT EXISTS ai_vector_db_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    db_type VARCHAR(20) NOT NULL COMMENT 'REDIS/CHROMA/MILVUS',
    host VARCHAR(100) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(100) COMMENT 'Milvus:default, Chroma:数据库名',
    collection_name VARCHAR(200) NOT NULL,
    extra_config TEXT COMMENT 'JSON格式额外配置',
    active TINYINT DEFAULT 0 COMMENT '1-激活 0-未激活',
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量数据库配置表';

-- 2.1 模型厂商表
CREATE TABLE IF NOT EXISTS ai_model_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    logo VARCHAR(200),
    code VARCHAR(30) NOT NULL UNIQUE,
    base_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500),
    description VARCHAR(500),
    enabled INT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型厂商表';

-- 2.2 模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    model_code VARCHAR(100) NOT NULL,
    model_type VARCHAR(20) NOT NULL,
    enabled INT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    support_tools INT NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_model_provider_id (provider_id),
    INDEX idx_model_enabled_sort (enabled, sort),
    INDEX idx_model_code_enabled (model_code, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 3. 会话表
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    model_id VARCHAR(50) COMMENT '当前使用的模型标识',
    type VARCHAR(20) DEFAULT 'CHAT' COMMENT 'CHAT-普通对话 RAG-RAG对话 SQL-SQL对话',
    knowledge_base_id BIGINT COMMENT 'RAG对话关联的知识库',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 4. 聊天消息表
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
    content TEXT NOT NULL,
    model_id VARCHAR(50),
    token_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- 5. 知识库表
CREATE TABLE IF NOT EXISTS ai_knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'KB-知识库 DDL-数据库DDL',
    system_prompt TEXT COMMENT '知识库关联的系统提示词',
    database_name VARCHAR(100) COMMENT 'DDL类型关联的数据库名',
    vector_db_type VARCHAR(20) COMMENT '使用的向量库类型',
    vector_collection VARCHAR(200) COMMENT '向量集合名称',
    description VARCHAR(500),
    document_count INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1-正常 0-处理中 -1-错误',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 6. 知识库文档表
CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(20) COMMENT 'txt/md/pdf/docx/xlsx/json/csv',
    file_size BIGINT,
    chunk_count INT DEFAULT 0 COMMENT '切分后的文档块数',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/ERROR',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_knowledge_base_id (knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- 7. Spring AI Chat Memory 表
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content JSON NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM/TOOL',
    `timestamp` TIMESTAMP NOT NULL,
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Spring AI对话记忆表';

-- 8. 工作流定义表
CREATE TABLE IF NOT EXISTS ai_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    description VARCHAR(500) COMMENT '描述',
    type VARCHAR(20) NOT NULL DEFAULT 'WORKFLOW' COMMENT '类型: WORKFLOW/CHAT',
    reply_requirements VARCHAR(1000) COMMENT 'CHAT 类型最终回复要求',
    graph_data JSON NOT NULL COMMENT '画布完整数据',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED',
    user_id BIGINT NOT NULL COMMENT '创建人ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- 9. 工作流执行记录表
CREATE TABLE IF NOT EXISTS ai_workflow_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workflow_id BIGINT NOT NULL COMMENT '关联工作流ID',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/COMPLETED/FAILED/CANCELLED',
    input JSON COMMENT '执行输入参数',
    output JSON COMMENT '最终输出结果',
    error_message TEXT COMMENT '错误信息',
    user_id BIGINT NOT NULL COMMENT '执行人ID',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    finished_at TIMESTAMP NULL COMMENT '结束时间',
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行记录表';

-- 10. 节点执行记录表
CREATE TABLE IF NOT EXISTS ai_workflow_node_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    execution_id BIGINT NOT NULL COMMENT '关联执行记录ID',
    node_id VARCHAR(50) NOT NULL COMMENT '画布中的节点ID',
    node_name VARCHAR(100) COMMENT '节点名称',
    node_type VARCHAR(30) NOT NULL COMMENT '节点类型: LLM/RAG/NL2SQL/CONDITION/TOOL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED/SKIPPED',
    input_data JSON COMMENT '节点输入',
    output_data JSON COMMENT '节点输出',
    error_message TEXT COMMENT '错误信息',
    started_at TIMESTAMP NULL COMMENT '开始时间',
    finished_at TIMESTAMP NULL COMMENT '结束时间',
    INDEX idx_execution_id (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行记录表';

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

-- 12. 角色表
CREATE TABLE IF NOT EXISTS ai_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '角色名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    status TINYINT DEFAULT 1 COMMENT '1-正常 0-禁用',
    sort INT DEFAULT 0 COMMENT '排序',
    remark VARCHAR(200) COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 13. 菜单表
CREATE TABLE IF NOT EXISTS ai_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT NULL COMMENT '父菜单ID',
    name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    path VARCHAR(200) COMMENT '路由路径',
    icon VARCHAR(100) COMMENT '菜单图标',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '1-正常 0-禁用',
    type TINYINT NOT NULL COMMENT '1-目录 2-菜单',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 14. 用户-角色关联表
CREATE TABLE IF NOT EXISTS ai_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 15. 角色-菜单关联表
CREATE TABLE IF NOT EXISTS ai_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';


-- 16. Agent 技能表（管理面唯一真相源）
CREATE TABLE IF NOT EXISTS ai_skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '技能目录名 kebab-case',
    display_name VARCHAR(128) COMMENT '展示名',
    description VARCHAR(512) NOT NULL COMMENT '一句话描述，注入技能目录',
    content LONGTEXT NOT NULL COMMENT '完整 SKILL.md 正文（含 front-matter）',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_APPROVAL/ACTIVE/REJECTED/ARCHIVED',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO_GENERATED',
    category VARCHAR(64) COMMENT '分组',
    tags VARCHAR(256) COMMENT '标签',
    quality_score DECIMAL(5,2) COMMENT '质量分 0-100',
    trial_result TEXT COMMENT '试运行结果 JSON',
    file_path VARCHAR(512) COMMENT '物化路径，ACTIVE 前为 NULL',
    parent_skill_id BIGINT COMMENT '版本血缘',
    author_user_id BIGINT COMMENT '作者',
    approved_by BIGINT COMMENT '审批人',
    approved_at DATETIME COMMENT '审批时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_name (name),
    INDEX idx_skill_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 技能表';
