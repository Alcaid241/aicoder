---
title: 系统管理模块（aicoder-system 微服务）
description: 新建 aicoder-system 微服务实现菜单级 RBAC 权限控制，包含用户管理、角色管理、权限管理、菜单管理四个子功能
status: active
---

## Purpose

新建 aicoder-system 微服务（端口 8085），实现菜单级 RBAC 权限控制。包含用户管理、角色管理、权限管理、菜单管理四个子功能，前端侧边栏从硬编码改为根据用户权限动态渲染。

## Requirements

### Requirement: The system SHALL provide user management with role assignment
User management SHALL support paginated listing, creation, update, deletion, status toggle, role assignment, and password reset.

#### Scenario: 分页查询用户列表
- **WHEN** 管理员 GET `/api/system/user/list` 携带分页参数
- **THEN** 返回用户列表，每条记录含角色信息

#### Scenario: 新增用户
- **WHEN** 管理员 POST `/api/system/user` 携带 username、password、nickname、email
- **THEN** 密码经 BCrypt 加密存储，创建 ai_user 记录

#### Scenario: 分配角色
- **WHEN** 管理员 PUT `/api/system/user/{id}/roles` 携带角色 ID 列表
- **THEN** 更新 ai_user_role 关联表，用户获得对应角色的菜单权限

#### Scenario: 启用/禁用用户
- **WHEN** 管理员 PUT `/api/system/user/{id}/status`
- **THEN** 切换用户 status 字段（1-正常/0-禁用）

#### Scenario: 重置密码
- **WHEN** 管理员 PUT `/api/system/user/{id}/reset-password`
- **THEN** 生成随机密码，BCrypt 加密后更新并返回新密码

### Requirement: The system SHALL provide role management with menu assignment
Role management SHALL support listing, creation, update, deletion, and assigning menus to roles.

#### Scenario: 角色列表
- **WHEN** 管理员 GET `/api/system/role/list`
- **THEN** 返回所有角色，含 name、code、status、sort

#### Scenario: 新增角色
- **WHEN** 管理员 POST `/api/system/role` 携带 name、code、remark
- **THEN** 创建 ai_role 记录，code 唯一约束

#### Scenario: 分配菜单给角色
- **WHEN** 管理员 PUT `/api/system/role/{id}/menus` 携带菜单 ID 列表
- **THEN** 更新 ai_role_menu 关联表，角色获得对应菜单的访问权限

#### Scenario: 获取角色已分配菜单
- **WHEN** 管理员 GET `/api/system/role/{id}/menus`
- **THEN** 返回该角色已分配的菜单 ID 列表

### Requirement: The system SHALL provide menu tree management
Menu management SHALL support tree-structured listing, creation, update, and deletion of menu items with directory/menu/button types.

#### Scenario: 获取完整菜单树
- **WHEN** 管理员 GET `/api/system/menu/tree`
- **THEN** 返回全部菜单树形结构（管理用），按 parent_id 层级嵌套

#### Scenario: 获取用户有权限的菜单树
- **WHEN** 登录用户 GET `/api/system/menu/user`
- **THEN** 根据用户角色从 ai_role_menu 关联表查询，返回该用户有权限访问的菜单树

#### Scenario: 新增菜单
- **WHEN** 管理员 POST `/api/system/menu` 携带 parentId、name、path、icon、sort、type
- **THEN** 创建 ai_menu 记录，type=1 为目录、type=2 为菜单

#### Scenario: 删除菜单
- **WHEN** 管理员 DELETE `/api/system/menu/{id}`
- **THEN** 若存在子菜单则级联处理，清理关联的 ai_role_menu 记录

### Requirement: The system SHALL provide permission management by role dimension
Permission management SHALL display all roles with their menu permissions in a role-centric view, supporting batch menu assignment.

#### Scenario: 角色维度权限查看
- **WHEN** 管理员 GET `/api/system/permission/roles`
- **THEN** 返回所有角色及其已分配的菜单权限列表

#### Scenario: 批量分配菜单
- **WHEN** 管理员 PUT `/api/system/permission/role/{roleId}/menus` 携带菜单 ID 列表
- **THEN** 批量更新 ai_role_menu 关联表

### Requirement: The system SHALL seed initial admin user, role, and menu data on startup
DataInitializer in aicoder-system SHALL auto-insert default super admin role, admin user (admin/admin123), and all initial menus if they do not exist.

#### Scenario: 超级管理员角色初始化
- **WHEN** system 模块启动且 SUPER_ADMIN 角色不存在
- **THEN** 自动插入超级管理员角色（code=SUPER_ADMIN），并分配所有菜单权限

#### Scenario: 管理员用户初始化
- **WHEN** admin 用户不存在
- **THEN** 自动创建 admin 用户（密码 admin123 经 BCrypt 加密），关联 SUPER_ADMIN 角色

#### Scenario: 初始菜单数据
- **WHEN** system 模块启动
- **THEN** 自动插入首页、对话、知识库、RAG对话、工作流、NL2SQL、向量库配置、个人信息、系统管理（含用户/角色/权限/菜单四个子菜单）等菜单记录

### Requirement: The system SHALL return user menu tree in login response
The admin login endpoint SHALL call aicoder-system to fetch the user's authorized menu tree and include it in LoginResponse.

#### Scenario: 登录返回菜单
- **WHEN** 用户 POST `/api/admin/login` 验证通过
- **THEN** aicoder-admin 通过 RestTemplate 调用 aicoder-system 获取用户菜单树，LoginResponse 中包含 menus 字段

#### Scenario: 前端存储菜单
- **WHEN** 前端收到登录响应中的 menus
- **THEN** 存入 menuStore，侧边栏 SideMenu.vue 从 menuStore 动态渲染，替代原有硬编码菜单

### Requirement: The system SHALL render dynamic sidebar menu with multi-level support
The frontend sidebar SHALL render menu items dynamically from menuStore, supporting expandable directory items with icons and arrow indicators.

#### Scenario: 多级菜单渲染
- **WHEN** menuStore 包含 type=1 的目录菜单及其子菜单
- **THEN** 侧边栏渲染为可展开的目录项（带图标 + 展开箭头），点击展开显示子菜单

#### Scenario: 无权限菜单隐藏
- **WHEN** 用户角色未分配某菜单
- **THEN** 该菜单不在 menuStore 中，侧边栏不渲染

### Requirement: The system SHALL route /api/system/** via Gateway
Gateway SHALL forward /api/system/** requests to lb://aicoder-system.

#### Scenario: 网关路由转发
- **WHEN** 前端访问 `/api/system/**`
- **THEN** Gateway 转发到 aicoder-system:8085，请求经 JWT 鉴权后携带 X-User-Id 头

### Requirement: The system SHALL integrate with existing admin login flow
aicoder-admin's AuthController SHALL verify credentials locally, then call aicoder-system for menu permissions.

#### Scenario: 登录流程整合
- **WHEN** 用户登录成功
- **THEN** AuthController 生成 JWT Token（含 userId、username），同时调用 aicoder-system 的菜单接口获取动态菜单，一并返回给前端

### Requirement: The system SHALL use five tables for RBAC data model
The RBAC data model SHALL consist of ai_role, ai_menu, ai_user_role, ai_role_menu tables plus the existing ai_user table.

#### Scenario: 角色-菜单直接关联
- **WHEN** 查询用户权限
- **THEN** 通过 ai_user → ai_user_role → ai_role → ai_role_menu → ai_menu 五表关联获取用户可访问的菜单列表
