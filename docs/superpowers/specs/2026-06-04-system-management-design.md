# 系统管理模块设计

## 概述

为 AI Coder 平台新增系统管理模块，包含用户管理、角色管理、权限管理、菜单管理四个子功能。采用菜单级 RBAC 权限控制，新建 `aicoder-system` 后端微服务模块。

## 需求决策

| 项 | 决定 |
|---|---|
| 权限粒度 | 菜单级控制 |
| 初始管理员 | 内置超级管理员（admin/admin123） |
| 前端呈现 | 侧边栏二级菜单（图标 + 展开箭头） |
| 后端模块 | 新建 aicoder-system 模块，端口 8085 |
| 数据模型 | 角色-菜单直接关联（5 张表） |

## 数据库设计

### 新增表

```sql
-- 角色表
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

-- 菜单表
CREATE TABLE IF NOT EXISTS ai_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT NULL COMMENT '父菜单ID',
    name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    path VARCHAR(200) COMMENT '路由路径',
    icon VARCHAR(100) COMMENT '菜单图标',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '1-正常 0-禁用',
    type TINYINT NOT NULL COMMENT '1-目录 2-菜单 3-按钮（预留）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS ai_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 角色-菜单关联表
CREATE TABLE IF NOT EXISTS ai_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';
```

### 初始数据

```sql
-- 超级管理员角色
INSERT INTO ai_role (name, code, status, sort, remark) VALUES ('超级管理员', 'SUPER_ADMIN', 1, 0, '拥有所有权限');

-- 菜单数据
INSERT INTO ai_menu (id, parent_id, name, path, icon, sort, type) VALUES
(1, NULL, '首页', '/home', 'Home', 1, 2),
(2, NULL, '对话', '/chat', 'Chat', 2, 2),
(3, NULL, '知识库', '/knowledge', 'Knowledge', 3, 2),
(4, NULL, 'RAG 对话', '/rag', 'Rag', 4, 2),
(5, NULL, '工作流', '/workflow', 'Workflow', 5, 2),
(6, NULL, 'NL2SQL', '/sql', 'Sql', 6, 2),
(7, NULL, '向量库配置', '/vectordb', 'VectorDb', 7, 2),
(8, NULL, '个人信息', '/profile', 'Profile', 8, 2),
(9, NULL, '系统管理', '/system', 'System', 9, 1),
(10, 9, '用户管理', '/system/user', 'User', 1, 2),
(11, 9, '角色管理', '/system/role', 'Role', 2, 2),
(12, 9, '权限管理', '/system/permission', 'Permission', 3, 2),
(13, 9, '菜单管理', '/system/menu', 'Menu', 4, 2);

-- 超级管理员拥有所有菜单
INSERT INTO ai_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM ai_role r, ai_menu m WHERE r.code = 'SUPER_ADMIN';

-- 内置管理员用户（密码 admin123，BCrypt 哈希在应用启动时由 DataInitializer 生成）
-- 初始数据通过 Java DataInitializer 类写入，避免硬编码 BCrypt 哈希

-- 管理员关联超级管理员角色
INSERT INTO ai_user_role (user_id, role_id)
SELECT u.id, r.id FROM ai_user u, ai_role r WHERE u.username = 'admin' AND r.code = 'SUPER_ADMIN';
```

## 后端架构

### 新建模块：aicoder-system

- 端口：**8085**
- 注册到 Nacos，服务名 `aicoder-system`
- 网关路由：`/api/system/**` -> `lb://aicoder-system`
- 使用 Spring Data JPA，连接同一 `test_ai` 数据库

### 包结构

```
com.ai.coder.system/
├── SystemApplication.java
├── config/
│   └── OpenApiConfig.java
├── controller/
│   ├── UserController.java
│   ├── RoleController.java
│   ├── MenuController.java
│   └── PermissionController.java
├── service/
│   ├── UserService.java
│   ├── RoleService.java
│   ├── MenuService.java
│   └── PermissionService.java
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── MenuRepository.java
│   ├── UserRoleRepository.java
│   └── RoleMenuRepository.java
├── entity/
│   ├── User.java
│   ├── Role.java
│   ├── Menu.java
│   ├── UserRole.java
│   └── RoleMenu.java
└── dto/
    ├── UserDTO.java
    ├── RoleDTO.java
    ├── MenuDTO.java
    ├── RoleMenuDTO.java
    └── PageResult.java
```

### API 端点设计

#### 用户管理 `/api/system/user`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/user/list` | 分页查询用户列表（含角色信息） |
| GET | `/api/system/user/{id}` | 获取用户详情 |
| POST | `/api/system/user` | 新增用户 |
| PUT | `/api/system/user/{id}` | 更新用户 |
| DELETE | `/api/system/user/{id}` | 删除用户 |
| PUT | `/api/system/user/{id}/status` | 启用/禁用用户 |
| PUT | `/api/system/user/{id}/roles` | 分配角色 |
| PUT | `/api/system/user/{id}/reset-password` | 重置密码 |

#### 角色管理 `/api/system/role`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/role/list` | 查询角色列表 |
| GET | `/api/system/role/{id}` | 获取角色详情 |
| POST | `/api/system/role` | 新增角色 |
| PUT | `/api/system/role/{id}` | 更新角色 |
| DELETE | `/api/system/role/{id}` | 删除角色 |
| GET | `/api/system/role/{id}/menus` | 获取角色已分配的菜单ID列表 |
| PUT | `/api/system/role/{id}/menus` | 分配菜单给角色 |

#### 菜单管理 `/api/system/menu`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/menu/tree` | 获取菜单树（管理用，全部菜单） |
| GET | `/api/system/menu/user` | 获取当前用户有权限的菜单树（登录后调用） |
| POST | `/api/system/menu` | 新增菜单 |
| PUT | `/api/system/menu/{id}` | 更新菜单 |
| DELETE | `/api/system/menu/{id}` | 删除菜单 |

#### 权限管理 `/api/system/permission`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/permission/roles` | 获取所有角色及其菜单权限（角色维度） |
| PUT | `/api/system/permission/role/{roleId}/menus` | 批量分配菜单给角色 |

### 登录流程变更

aicoder-admin 的 `AuthController` 登录接口需要：
1. 验证用户名密码（不变）
2. 调用 aicoder-system 服务获取用户菜单权限
3. 在 LoginResponse 中返回 `menus` 字段（菜单树）

前端登录后存储菜单到 `userStore`，侧边栏从硬编码改为动态渲染。

## 前端设计

### 新增文件

```
aicoder-web/src/
├── api/
│   └── system.ts                 # 系统管理 API
├── views/
│   └── system/
│       ├── UserManageView.vue     # 用户管理
│       ├── RoleManageView.vue     # 角色管理
│       ├── PermissionView.vue     # 权限管理
│       └── MenuManageView.vue     # 菜单管理
└── stores/
    └── menuStore.ts               # 菜单状态管理（替代硬编码菜单）
```

### 路由变更

在 `router/index.ts` 中新增系统管理路由：

```typescript
{
  path: '/system',
  name: 'System',
  redirect: '/system/user',
  children: [
    { path: 'user', name: 'SystemUser', component: () => import('@/views/system/UserManageView.vue') },
    { path: 'role', name: 'SystemRole', component: () => import('@/views/system/RoleManageView.vue') },
    { path: 'permission', name: 'SystemPermission', component: () => import('@/views/system/PermissionView.vue') },
    { path: 'menu', name: 'SystemMenu', component: () => import('@/views/system/MenuManageView.vue') },
  ]
}
```

### 侧边栏变更

SideMenu.vue 从硬编码菜单改为从 menuStore 读取动态菜单。支持多级菜单渲染（系统管理作为可展开的目录项，带图标 + 展开箭头）。

### 页面功能

**用户管理**：表格展示用户列表，支持搜索、新增/编辑弹窗、删除确认、启用/禁用切换、分配角色弹窗、重置密码。

**角色管理**：表格展示角色列表，支持新增/编辑弹窗、删除确认。分配菜单使用树形勾选弹窗。

**权限管理**：以角色为维度，左侧角色列表，右侧树形菜单勾选，直观展示和修改角色-菜单关系。

**菜单管理**：树形表格展示菜单层级，支持新增/编辑弹窗（选择父菜单、填写名称/路径/图标/排序）。

## 网关变更

gateway `application.yml` 新增路由：

```yaml
- id: system-service
  uri: lb://aicoder-system
  predicates:
    - Path=/api/system/**
```

gateway `JwtAuthFilter` 白名单不变（登录/注册仍走 admin 模块）。`/api/system/**` 需 JWT 认证，通过 `X-User-Id` 请求头传递用户 ID。

## 依赖关系

- aicoder-system 依赖：spring-boot-starter-web、spring-boot-starter-data-jpa、mysql-connector-j、spring-cloud-starter-alibaba-nacos-discovery、springdoc、spring-security-crypto、lombok、java-jwt
- 前端无新依赖，使用现有 Vue Router + Pinia + Axios
- aicoder-admin 登录接口需调用 aicoder-system（通过 RestTemplate 或 OpenFeign）
