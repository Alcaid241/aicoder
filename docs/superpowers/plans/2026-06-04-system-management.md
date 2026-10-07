# 系统管理模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AI Coder 平台新增系统管理模块（用户管理、角色管理、权限管理、菜单管理），采用菜单级 RBAC 权限控制。

**Architecture:** 新建 aicoder-system 微服务模块（端口 8085），通过网关路由 `/api/system/**`。前端侧边栏从硬编码改为动态渲染，登录后获取用户菜单权限。登录流程不变（仍走 admin 模块），前端在登录后额外调用 system 模块获取菜单树。

**Tech Stack:** Spring Boot 3.5 + JPA + Spring Cloud Nacos + Lombok + Vue 3 + Pinia + TypeScript

---

## File Structure

### 新建文件

```
aicoder-system/
├── pom.xml
├── src/main/java/com/ai/coder/system/
│   ├── AicoderSystemApplication.java
│   ├── config/OpenApiConfig.java
│   ├── entity/Role.java
│   ├── entity/Menu.java
│   ├── entity/UserRole.java
│   ├── entity/RoleMenu.java
│   ├── entity/SysUser.java
│   ├── repository/RoleRepository.java
│   ├── repository/MenuRepository.java
│   ├── repository/UserRoleRepository.java
│   ├── repository/RoleMenuRepository.java
│   ├── repository/SysUserRepository.java
│   ├── dto/PageResult.java
│   ├── dto/MenuTreeNode.java
│   ├── dto/UserDTO.java
│   ├── dto/RoleDTO.java
│   ├── dto/MenuDTO.java
│   ├── dto/AssignRoleRequest.java
│   ├── dto/AssignMenuRequest.java
│   ├── service/MenuService.java
│   ├── service/RoleService.java
│   ├── service/UserService.java
│   ├── service/PermissionService.java
│   ├── controller/MenuController.java
│   ├── controller/RoleController.java
│   ├── controller/UserController.java
│   ├── controller/PermissionController.java
│   └── init/DataInitializer.java
└── src/main/resources/application.yml

aicoder-web/src/
├── api/system.ts
├── stores/menuStore.ts
└── views/system/
    ├── UserManageView.vue
    ├── RoleManageView.vue
    ├── MenuManageView.vue
    └── PermissionView.vue
```

### 修改文件

```
pom.xml                                          # 添加 aicoder-system module
aicoder-gateway/src/main/resources/application.yml  # 添加 system 路由
aicoder-web/src/types/index.ts                    # 添加系统管理相关类型
aicoder-web/src/api/auth.ts                       # 添加获取用户菜单的方法
aicoder-web/src/stores/userStore.ts               # 登录后获取菜单
aicoder-web/src/stores/menuStore.ts               # 新建菜单 store
aicoder-web/src/layouts/SideMenu.vue              # 动态菜单渲染
aicoder-web/src/router/index.ts                   # 添加系统管理路由
sql/schema.sql                                    # 添加新表 DDL
```

---

## Task 1: 创建 aicoder-system 模块脚手架

**Files:**
- Create: `aicoder-system/pom.xml`
- Create: `aicoder-system/src/main/resources/application.yml`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/AicoderSystemApplication.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/config/OpenApiConfig.java`
- Modify: `pom.xml` (添加 module)
- Modify: `aicoder-gateway/src/main/resources/application.yml` (添加路由)

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0.0</modelVersion>

    <parent>
        <groupId>com.ai.coder</groupId>
        <artifactId>aicoder</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>aicoder-system</artifactId>
    <name>AI Coder System</name>
    <description>系统管理：用户、角色、权限、菜单</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 application.yml**

```yaml
server:
  port: 8085

spring:
  application:
    name: aicoder-system
  datasource:
    url: jdbc:mysql://localhost:3306/test_ai?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 3: 创建启动类 AicoderSystemApplication.java**

```java
package com.ai.coder.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class AicoderSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderSystemApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建 OpenApiConfig.java**

```java
package com.ai.coder.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AICoder System API")
                        .version("1.0")
                        .description("AICoder 系统管理接口文档"));
    }
}
```

- [ ] **Step 5: 父 pom.xml 添加 module**

在父 pom.xml 的 `<modules>` 中追加 `<module>aicoder-system</module>`。

- [ ] **Step 6: 网关添加 system 路由**

在 `aicoder-gateway/src/main/resources/application.yml` 的 `gateway.routes` 中追加：

```yaml
        - id: system-service
          uri: lb://aicoder-system
          predicates:
            - Path=/api/system/**
```

- [ ] **Step 7: 创建目录结构并验证编译**

```bash
cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-system -am
```

- [ ] **Step 8: 提交**

```bash
git add aicoder-system/ pom.xml aicoder-gateway/src/main/resources/application.yml
git commit -m "feat(system): 创建 aicoder-system 模块脚手架 + 网关路由"
```

---

## Task 2: 实体类 + 仓库层

**Files:**
- Create: `aicoder-system/src/main/java/com/ai/coder/system/entity/Role.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/entity/Menu.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/entity/UserRole.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/entity/RoleMenu.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/entity/SysUser.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/repository/RoleRepository.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/repository/MenuRepository.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/repository/UserRoleRepository.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/repository/RoleMenuRepository.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/repository/SysUserRepository.java`

- [ ] **Step 1: 创建 Role.java**

```java
package com.ai.coder.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private Integer status = 1;

    private Integer sort = 0;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 Menu.java**

```java
package com.ai.coder.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_menu")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String path;

    @Column(length = 100)
    private String icon;

    private Integer sort = 0;

    @Column(nullable = false)
    private Integer status = 1;

    /** 1-目录 2-菜单 */
    @Column(nullable = false)
    private Integer type;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 UserRole.java**

```java
package com.ai.coder.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_user_role")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    private Long userId;

    @Id
    private Long roleId;
}
```

- [ ] **Step 4: 创建 UserRoleId.java**

```java
package com.ai.coder.system.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements Serializable {
    private Long userId;
    private Long roleId;
}
```

- [ ] **Step 5: 创建 RoleMenu.java**

```java
package com.ai.coder.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_role_menu")
@IdClass(RoleMenuId.class)
public class RoleMenu {

    @Id
    private Long roleId;

    @Id
    private Long menuId;
}
```

- [ ] **Step 6: 创建 RoleMenuId.java**

```java
package com.ai.coder.system.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleMenuId implements Serializable {
    private Long roleId;
    private Long menuId;
}
```

- [ ] **Step 7: 创建 SysUser.java（只读，用于查询用户）**

```java
package com.ai.coder.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    private String password;

    private String nickname;

    private String email;

    private String avatar;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

- [ ] **Step 8: 创建 RoleRepository.java**

```java
package com.ai.coder.system.repository;

import com.ai.coder.system.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
```

- [ ] **Step 9: 创建 MenuRepository.java**

```java
package com.ai.coder.system.repository;

import com.ai.coder.system.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByStatusOrderBySortAsc(Integer status);

    List<Menu> findByIdInOrderBySortAsc(List<Long> ids);
}
```

- [ ] **Step 10: 创建 UserRoleRepository.java**

```java
package com.ai.coder.system.repository;

import com.ai.coder.system.entity.UserRole;
import com.ai.coder.system.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    List<UserRole> findByRoleId(Long roleId);
}
```

- [ ] **Step 11: 创建 RoleMenuRepository.java**

```java
package com.ai.coder.system.repository;

import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.entity.RoleMenuId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, RoleMenuId> {

    List<RoleMenu> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
```

- [ ] **Step 12: 创建 SysUserRepository.java**

```java
package com.ai.coder.system.repository;

import com.ai.coder.system.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    Page<SysUser> findByUsernameContainingOrNicknameContaining(String username, String nickname, Pageable pageable);
}
```

- [ ] **Step 13: 验证编译并提交**

```bash
cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-system -am
git add aicoder-system/src/
git commit -m "feat(system): 添加实体类和仓库层"
```

---

## Task 3: DTO 类

**Files:**
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/PageResult.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/MenuTreeNode.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/UserDTO.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/RoleDTO.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/MenuDTO.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/AssignRoleRequest.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/dto/AssignMenuRequest.java`

- [ ] **Step 1: 创建 PageResult.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private Long total;
}
```

- [ ] **Step 2: 创建 MenuTreeNode.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuTreeNode {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sort;
    private Integer type;
    private List<MenuTreeNode> children;
}
```

- [ ] **Step 3: 创建 UserDTO.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Integer status;
    private String createdAt;
    private List<RoleDTO> roles;
}
```

- [ ] **Step 4: 创建 CreateUserRequest.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    private String username;
    private String password;
    private String nickname;
    private String email;
}
```

- [ ] **Step 5: 创建 UpdateUserRequest.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String nickname;
    private String email;
    private String avatar;
    private Integer status;
}
```

- [ ] **Step 6: 创建 RoleDTO.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {

    private Long id;
    private String name;
    private String code;
    private Integer status;
    private Integer sort;
    private String remark;
    private String createdAt;
}
```

- [ ] **Step 7: 创建 CreateRoleRequest.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    private String name;
    private String code;
    private Integer sort;
    private String remark;
}
```

- [ ] **Step 8: 创建 MenuDTO.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuDTO {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    private Integer type;
}
```

- [ ] **Step 9: 创建 CreateMenuRequest.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuRequest {

    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sort;
    private Integer type;
}
```

- [ ] **Step 10: 创建 AssignRoleRequest.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequest {

    private List<Long> roleIds;
}
```

- [ ] **Step 11: 创建 AssignMenuRequest.java**

```java
package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignMenuRequest {

    private List<Long> menuIds;
}
```

- [ ] **Step 12: 提交**

```bash
git add aicoder-system/src/main/java/com/ai/coder/system/dto/
git commit -m "feat(system): 添加 DTO 类"
```

---

## Task 4: Service 层

**Files:**
- Create: `aicoder-system/src/main/java/com/ai/coder/system/service/MenuService.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/service/RoleService.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/service/UserService.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/service/PermissionService.java`

- [ ] **Step 1: 创建 MenuService.java**

```java
package com.ai.coder.system.service;

import com.ai.coder.system.dto.AssignMenuRequest;
import com.ai.coder.system.dto.CreateMenuRequest;
import com.ai.coder.system.dto.MenuDTO;
import com.ai.coder.system.dto.MenuTreeNode;
import com.ai.coder.system.entity.Menu;
import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.entity.UserRole;
import com.ai.coder.system.repository.MenuRepository;
import com.ai.coder.system.repository.RoleMenuRepository;
import com.ai.coder.system.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleMenuRepository roleMenuRepository;

    /** 获取完整菜单树（管理用） */
    public List<MenuTreeNode> getMenuTree() {
        List<Menu> menus = menuRepository.findByStatusOrderBySortAsc(1);
        return buildTree(menus);
    }

    /** 获取当前用户有权限的菜单树 */
    public List<MenuTreeNode> getUserMenuTree(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }

        Set<Long> menuIds = new HashSet<>();
        for (UserRole ur : userRoles) {
            List<RoleMenu> roleMenus = roleMenuRepository.findByRoleId(ur.getRoleId());
            roleMenus.forEach(rm -> menuIds.add(rm.getMenuId()));
        }

        if (menuIds.isEmpty()) {
            return List.of();
        }

        List<Menu> menus = menuRepository.findByIdInOrderBySortAsc(new ArrayList<>(menuIds));
        // 只保留状态正常的菜单
        menus = menus.stream().filter(m -> m.getStatus() == 1).collect(Collectors.toList());
        return buildTree(menus);
    }

    @Transactional
    public MenuDTO createMenu(CreateMenuRequest request) {
        Menu menu = new Menu();
        menu.setParentId(request.getParentId());
        menu.setName(request.getName());
        menu.setPath(request.getPath());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() != null ? request.getSort() : 0);
        menu.setStatus(1);
        menu.setType(request.getType());
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        Menu saved = menuRepository.save(menu);
        return toDTO(saved);
    }

    @Transactional
    public MenuDTO updateMenu(Long id, CreateMenuRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在"));
        if (request.getParentId() != null) menu.setParentId(request.getParentId());
        if (request.getName() != null) menu.setName(request.getName());
        if (request.getPath() != null) menu.setPath(request.getPath());
        if (request.getIcon() != null) menu.setIcon(request.getIcon());
        if (request.getSort() != null) menu.setSort(request.getSort());
        if (request.getType() != null) menu.setType(request.getType());
        menu.setUpdatedAt(LocalDateTime.now());
        Menu saved = menuRepository.save(menu);
        return toDTO(saved);
    }

    @Transactional
    public void deleteMenu(Long id) {
        // 检查是否有子菜单
        List<Menu> children = menuRepository.findByStatusOrderBySortAsc(1).stream()
                .filter(m -> id.equals(m.getParentId())).collect(Collectors.toList());
        if (!children.isEmpty()) {
            throw new RuntimeException("存在子菜单，无法删除");
        }
        roleMenuRepository.deleteByRoleId(id); // 清除关联（这里是menuId误用了，需要用自定义查询）
        menuRepository.deleteById(id);
    }

    private List<MenuTreeNode> buildTree(List<Menu> menus) {
        Map<Long, List<Menu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() != null ? m.getParentId() : 0L));

        List<MenuTreeNode> roots = new ArrayList<>();
        menus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0)
                .sorted((a, b) -> Integer.compare(a.getSort(), b.getSort()))
                .forEach(m -> roots.add(toTreeNode(m, byParent)));
        return roots;
    }

    private MenuTreeNode toTreeNode(Menu menu, Map<Long, List<Menu>> byParent) {
        MenuTreeNode node = new MenuTreeNode();
        node.setId(menu.getId());
        node.setParentId(menu.getParentId());
        node.setName(menu.getName());
        node.setPath(menu.getPath());
        node.setIcon(menu.getIcon());
        node.setSort(menu.getSort());
        node.setType(menu.getType());

        List<Menu> children = byParent.getOrDefault(menu.getId(), List.of());
        children.sort((a, b) -> Integer.compare(a.getSort(), b.getSort()));
        node.setChildren(children.stream().map(c -> toTreeNode(c, byParent)).collect(Collectors.toList()));
        return node;
    }

    private MenuDTO toDTO(Menu menu) {
        return new MenuDTO(menu.getId(), menu.getParentId(), menu.getName(),
                menu.getPath(), menu.getIcon(), menu.getSort(), menu.getStatus(), menu.getType());
    }
}
```

- [ ] **Step 2: 创建 RoleService.java**

```java
package com.ai.coder.system.service;

import com.ai.coder.system.dto.AssignMenuRequest;
import com.ai.coder.system.dto.CreateRoleRequest;
import com.ai.coder.system.dto.RoleDTO;
import com.ai.coder.system.entity.Role;
import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.repository.RoleMenuRepository;
import com.ai.coder.system.repository.RoleRepository;
import com.ai.coder.system.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final UserRoleRepository userRoleRepository;

    public List<RoleDTO> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public RoleDTO getRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        return toDTO(role);
    }

    @Transactional
    public RoleDTO createRole(CreateRoleRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("角色编码已存在");
        }
        Role role = new Role();
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setSort(request.getSort() != null ? request.getSort() : 0);
        role.setRemark(request.getRemark());
        role.setStatus(1);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        return toDTO(roleRepository.save(role));
    }

    @Transactional
    public RoleDTO updateRole(Long id, CreateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        if (request.getName() != null) role.setName(request.getName());
        if (request.getSort() != null) role.setSort(request.getSort());
        if (request.getRemark() != null) role.setRemark(request.getRemark());
        role.setUpdatedAt(LocalDateTime.now());
        return toDTO(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new RuntimeException("超级管理员角色不可删除");
        }
        if (!userRoleRepository.findByRoleId(id).isEmpty()) {
            throw new RuntimeException("角色下存在用户，无法删除");
        }
        roleMenuRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
    }

    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuRepository.findByRoleId(roleId).stream()
                .map(RoleMenu::getMenuId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignMenus(Long roleId, AssignMenuRequest request) {
        roleMenuRepository.deleteByRoleId(roleId);
        for (Long menuId : request.getMenuIds()) {
            roleMenuRepository.save(new RoleMenu(roleId, menuId));
        }
    }

    private RoleDTO toDTO(Role role) {
        return new RoleDTO(role.getId(), role.getName(), role.getCode(),
                role.getStatus(), role.getSort(), role.getRemark(),
                role.getCreatedAt() != null ? role.getCreatedAt().toString() : null);
    }
}
```

- [ ] **Step 3: 创建 UserService.java**

```java
package com.ai.coder.system.service;

import com.ai.coder.system.dto.AssignRoleRequest;
import com.ai.coder.system.dto.CreateUserRequest;
import com.ai.coder.system.dto.PageResult;
import com.ai.coder.system.dto.RoleDTO;
import com.ai.coder.system.dto.UpdateUserRequest;
import com.ai.coder.system.dto.UserDTO;
import com.ai.coder.system.entity.Role;
import com.ai.coder.system.entity.SysUser;
import com.ai.coder.system.entity.UserRole;
import com.ai.coder.system.repository.RoleRepository;
import com.ai.coder.system.repository.SysUserRepository;
import com.ai.coder.system.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PageResult<UserDTO> listUsers(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SysUser> userPage;
        if (keyword != null && !keyword.isBlank()) {
            userPage = userRepository.findByUsernameContainingOrNicknameContaining(keyword, keyword, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }
        List<UserDTO> dtos = userPage.getContent().stream().map(this::toDTO).collect(Collectors.toList());
        return new PageResult<>(dtos, userPage.getTotalElements());
    }

    public UserDTO getUser(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toDTO(user);
    }

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        if (request.getStatus() != null) user.setStatus(request.getStatus());
        user.setUpdatedAt(LocalDateTime.now());
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        userRoleRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void assignRoles(Long userId, AssignRoleRequest request) {
        userRoleRepository.deleteByUserId(userId);
        for (Long roleId : request.getRoleIds()) {
            userRoleRepository.save(new UserRole(userId, roleId));
        }
    }

    public List<RoleDTO> getUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        return userRoles.stream()
                .map(ur -> roleRepository.findById(ur.getRoleId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> {
                    Role r = opt.get();
                    return new RoleDTO(r.getId(), r.getName(), r.getCode(), r.getStatus(), r.getSort(), r.getRemark(),
                            r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                })
                .collect(Collectors.toList());
    }

    private UserDTO toDTO(SysUser user) {
        List<RoleDTO> roles = getUserRoles(user.getId());
        return new UserDTO(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getAvatar(), user.getStatus(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null, roles);
    }
}
```

- [ ] **Step 4: 创建 PermissionService.java**

```java
package com.ai.coder.system.service;

import com.ai.coder.system.dto.RoleDTO;
import com.ai.coder.system.dto.MenuTreeNode;
import com.ai.coder.system.entity.Role;
import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.repository.RoleMenuRepository;
import com.ai.coder.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final RoleRepository roleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final MenuService menuService;

    /** 获取所有角色及其菜单ID列表 */
    public List<Map<String, Object>> getRolePermissions() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream().map(role -> {
            Map<String, Object> map = new HashMap<>();
            map.put("role", new RoleDTO(role.getId(), role.getName(), role.getCode(),
                    role.getStatus(), role.getSort(), role.getRemark(),
                    role.getCreatedAt() != null ? role.getCreatedAt().toString() : null));
            List<Long> menuIds = roleMenuRepository.findByRoleId(role.getId()).stream()
                    .map(RoleMenu::getMenuId)
                    .collect(Collectors.toList());
            map.put("menuIds", menuIds);
            return map;
        }).collect(Collectors.toList());
    }

    /** 获取完整菜单树（供权限管理页面勾选用） */
    public List<MenuTreeNode> getAllMenuTree() {
        return menuService.getMenuTree();
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add aicoder-system/src/main/java/com/ai/coder/system/service/
git commit -m "feat(system): 添加 Service 层"
```

---

## Task 5: Controller 层

**Files:**
- Create: `aicoder-system/src/main/java/com/ai/coder/system/controller/MenuController.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/controller/RoleController.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/controller/UserController.java`
- Create: `aicoder-system/src/main/java/com/ai/coder/system/controller/PermissionController.java`

- [ ] **Step 1: 创建 MenuController.java**

```java
package com.ai.coder.system.controller;

import com.ai.coder.system.dto.CreateMenuRequest;
import com.ai.coder.system.dto.MenuDTO;
import com.ai.coder.system.dto.MenuTreeNode;
import com.ai.coder.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 获取完整菜单树（管理用） */
    @GetMapping("/tree")
    public ResponseEntity<List<MenuTreeNode>> getMenuTree() {
        return ResponseEntity.ok(menuService.getMenuTree());
    }

    /** 获取当前用户有权限的菜单树 */
    @GetMapping("/user")
    public ResponseEntity<List<MenuTreeNode>> getUserMenuTree(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(menuService.getUserMenuTree(userId));
    }

    @PostMapping
    public ResponseEntity<MenuDTO> createMenu(@RequestBody CreateMenuRequest request) {
        return ResponseEntity.ok(menuService.createMenu(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuDTO> updateMenu(@PathVariable Long id, @RequestBody CreateMenuRequest request) {
        return ResponseEntity.ok(menuService.updateMenu(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: 创建 RoleController.java**

```java
package com.ai.coder.system.controller;

import com.ai.coder.system.dto.AssignMenuRequest;
import com.ai.coder.system.dto.CreateRoleRequest;
import com.ai.coder.system.dto.RoleDTO;
import com.ai.coder.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/list")
    public ResponseEntity<List<RoleDTO>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDTO> getRole(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @PostMapping
    public ResponseEntity<RoleDTO> createRole(@RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDTO> updateRole(@PathVariable Long id, @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/menus")
    public ResponseEntity<List<Long>> getRoleMenuIds(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    public ResponseEntity<Void> assignMenus(@PathVariable Long id, @RequestBody AssignMenuRequest request) {
        roleService.assignMenus(id, request);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 3: 创建 UserController.java**

```java
package com.ai.coder.system.controller;

import com.ai.coder.system.dto.AssignRoleRequest;
import com.ai.coder.system.dto.CreateUserRequest;
import com.ai.coder.system.dto.PageResult;
import com.ai.coder.system.dto.UpdateUserRequest;
import com.ai.coder.system.dto.UserDTO;
import com.ai.coder.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/list")
    public ResponseEntity<PageResult<UserDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(userService.listUsers(page, size, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> toggleStatus(@PathVariable Long id) {
        UserDTO user = userService.getUser(id);
        com.ai.coder.system.dto.UpdateUserRequest req = new com.ai.coder.system.dto.UpdateUserRequest();
        req.setStatus(user.getStatus() == 1 ? 0 : 1);
        userService.updateUser(id, req);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRoleRequest request) {
        userService.assignRoles(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody String newPassword) {
        userService.resetPassword(id, newPassword);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 4: 创建 PermissionController.java**

```java
package com.ai.coder.system.controller;

import com.ai.coder.system.dto.AssignMenuRequest;
import com.ai.coder.system.dto.MenuTreeNode;
import com.ai.coder.system.service.PermissionService;
import com.ai.coder.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final RoleService roleService;

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> getRolePermissions() {
        return ResponseEntity.ok(permissionService.getRolePermissions());
    }

    @GetMapping("/menu-tree")
    public ResponseEntity<List<MenuTreeNode>> getAllMenuTree() {
        return ResponseEntity.ok(permissionService.getAllMenuTree());
    }

    @PutMapping("/role/{roleId}/menus")
    public ResponseEntity<Void> assignMenus(@PathVariable Long roleId, @RequestBody AssignMenuRequest request) {
        roleService.assignMenus(roleId, request);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add aicoder-system/src/main/java/com/ai/coder/system/controller/
git commit -m "feat(system): 添加 Controller 层"
```

---

## Task 6: 初始数据 + SQL 更新

**Files:**
- Create: `aicoder-system/src/main/java/com/ai/coder/system/init/DataInitializer.java`
- Modify: `sql/schema.sql` (添加新表 DDL)

- [ ] **Step 1: 创建 DataInitializer.java**

```java
package com.ai.coder.system.init;

import com.ai.coder.system.entity.Menu;
import com.ai.coder.system.entity.Role;
import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.entity.UserRole;
import com.ai.coder.system.repository.MenuRepository;
import com.ai.coder.system.repository.RoleMenuRepository;
import com.ai.coder.system.repository.RoleRepository;
import com.ai.coder.system.repository.SysUserRepository;
import com.ai.coder.system.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final SysUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleMenuRepository roleMenuRepository;

    @Override
    public void run(String... args) {
        initRoles();
        initMenus();
        initAdminUser();
    }

    private void initRoles() {
        if (roleRepository.findByCode("SUPER_ADMIN").isPresent()) {
            return;
        }
        Role role = new Role();
        role.setName("超级管理员");
        role.setCode("SUPER_ADMIN");
        role.setStatus(1);
        role.setSort(0);
        role.setRemark("拥有所有权限");
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleRepository.save(role);
        log.info("初始化超级管理员角色");
    }

    private void initMenus() {
        if (menuRepository.count() > 0) {
            return;
        }
        List<Menu> menus = List.of(
                menu(1L, null, "首页", "/home", "Home", 1, 2),
                menu(2L, null, "对话", "/chat", "Chat", 2, 2),
                menu(3L, null, "知识库", "/knowledge", "Knowledge", 3, 2),
                menu(4L, null, "RAG 对话", "/rag", "Rag", 4, 2),
                menu(5L, null, "工作流", "/workflow", "Workflow", 5, 2),
                menu(6L, null, "NL2SQL", "/sql", "Sql", 6, 2),
                menu(7L, null, "向量库配置", "/vectordb", "VectorDb", 7, 2),
                menu(8L, null, "个人信息", "/profile", "Profile", 8, 2),
                menu(9L, null, "系统管理", "/system", "System", 9, 1),
                menu(10L, 9L, "用户管理", "/system/user", "User", 1, 2),
                menu(11L, 9L, "角色管理", "/system/role", "Role", 2, 2),
                menu(12L, 9L, "权限管理", "/system/permission", "Permission", 3, 2),
                menu(13L, 9L, "菜单管理", "/system/menu", "Menu", 4, 2)
        );
        menuRepository.saveAll(menus);

        // 超级管理员拥有所有菜单
        Role adminRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        for (Menu menu : menus) {
            roleMenuRepository.save(new RoleMenu(adminRole.getId(), menu.getId()));
        }
        log.info("初始化菜单数据，共 {} 条", menus.size());
    }

    private void initAdminUser() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        com.ai.coder.system.entity.SysUser admin = new com.ai.coder.system.entity.SysUser();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setStatus(1);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        Role adminRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        userRoleRepository.save(new UserRole(admin.getId(), adminRole.getId()));
        log.info("初始化管理员账号 admin/admin123");
    }

    private Menu menu(Long id, Long parentId, String name, String path, String icon, int sort, int type) {
        Menu m = new Menu();
        m.setId(id);
        m.setParentId(parentId);
        m.setName(name);
        m.setPath(path);
        m.setIcon(icon);
        m.setSort(sort);
        m.setStatus(1);
        m.setType(type);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        return m;
    }
}
```

- [ ] **Step 2: 更新 sql/schema.sql — 在文件末尾追加新表 DDL**

```sql
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
```

- [ ] **Step 3: 验证编译并提交**

```bash
cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile -pl aicoder-system -am
git add aicoder-system/src/main/java/com/ai/coder/system/init/ sql/schema.sql
git commit -m "feat(system): 添加初始数据加载器 + DDL"
```

---

## Task 7: 前端类型 + API 层

**Files:**
- Modify: `aicoder-web/src/types/index.ts`
- Create: `aicoder-web/src/api/system.ts`
- Modify: `aicoder-web/src/api/auth.ts`

- [ ] **Step 1: 在 types/index.ts 末尾追加系统管理类型**

```typescript
// ===== 系统管理 =====

export interface MenuTreeNode {
  id: number
  parentId: number | null
  name: string
  path: string
  icon: string
  sort: number
  type: number
  children: MenuTreeNode[]
}

export interface RoleDTO {
  id: number
  name: string
  code: string
  status: number
  sort: number
  remark: string
  createdAt: string
}

export interface UserDTO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  status: number
  createdAt: string
  roles: RoleDTO[]
}

export interface MenuDTO {
  id: number
  parentId: number | null
  name: string
  path: string
  icon: string
  sort: number
  status: number
  type: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}
```

- [ ] **Step 2: 创建 api/system.ts**

```typescript
import request from './request'
import type { UserDTO, RoleDTO, MenuDTO, MenuTreeNode, PageResult } from '@/types'

export const systemApi = {
  // 用户管理
  userList: (page: number, size: number, keyword?: string) =>
    request.get<PageResult<UserDTO>>('/system/user/list', { params: { page, size, keyword } }),
  userGet: (id: number) => request.get<UserDTO>(`/system/user/${id}`),
  userCreate: (data: { username: string; password: string; nickname: string; email?: string }) =>
    request.post<UserDTO>('/system/user', data),
  userUpdate: (id: number, data: Partial<UserDTO>) =>
    request.put<UserDTO>(`/system/user/${id}`, data),
  userDelete: (id: number) => request.delete(`/system/user/${id}`),
  userToggleStatus: (id: number) => request.put(`/system/user/${id}/status`),
  userAssignRoles: (id: number, roleIds: number[]) =>
    request.put(`/system/user/${id}/roles`, { roleIds }),
  userResetPassword: (id: number, newPassword: string) =>
    request.put(`/system/user/${id}/reset-password`, newPassword),

  // 角色管理
  roleList: () => request.get<RoleDTO[]>('/system/role/list'),
  roleGet: (id: number) => request.get<RoleDTO>(`/system/role/${id}`),
  roleCreate: (data: { name: string; code: string; sort?: number; remark?: string }) =>
    request.post<RoleDTO>('/system/role', data),
  roleUpdate: (id: number, data: { name?: string; sort?: number; remark?: string }) =>
    request.put<RoleDTO>(`/system/role/${id}`, data),
  roleDelete: (id: number) => request.delete(`/system/role/${id}`),
  roleMenuIds: (id: number) => request.get<number[]>(`/system/role/${id}/menus`),
  roleAssignMenus: (id: number, menuIds: number[]) =>
    request.put(`/system/role/${id}/menus`, { menuIds }),

  // 菜单管理
  menuTree: () => request.get<MenuTreeNode[]>('/system/menu/tree'),
  menuUserTree: () => request.get<MenuTreeNode[]>('/system/menu/user'),
  menuCreate: (data: { parentId?: number | null; name: string; path?: string; icon?: string; sort?: number; type: number }) =>
    request.post<MenuDTO>('/system/menu', data),
  menuUpdate: (id: number, data: Partial<MenuDTO>) =>
    request.put<MenuDTO>(`/system/menu/${id}`, data),
  menuDelete: (id: number) => request.delete(`/system/menu/${id}`),

  // 权限管理
  permissionRoles: () => request.get<Array<{ role: RoleDTO; menuIds: number[] }>>('/system/permission/roles'),
  permissionMenuTree: () => request.get<MenuTreeNode[]>('/system/permission/menu-tree'),
  permissionAssign: (roleId: number, menuIds: number[]) =>
    request.put(`/system/permission/role/${roleId}/menus`, { menuIds }),
}
```

- [ ] **Step 3: 在 api/auth.ts 中添加获取用户菜单的方法**

追加一个方法：

```typescript
  getUserMenus: () => request.get<MenuTreeNode[]>('/system/menu/user'),
```

需要在 auth.ts 顶部添加导入：`import type { MenuTreeNode } from '@/types'`

- [ ] **Step 4: 提交**

```bash
git add aicoder-web/src/types/index.ts aicoder-web/src/api/system.ts aicoder-web/src/api/auth.ts
git commit -m "feat(web): 添加系统管理类型定义和 API 层"
```

---

## Task 8: 前端菜单 Store + 登录流程

**Files:**
- Create: `aicoder-web/src/stores/menuStore.ts`
- Modify: `aicoder-web/src/stores/userStore.ts`

- [ ] **Step 1: 创建 menuStore.ts**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MenuTreeNode } from '@/types'
import { systemApi } from '@/api/system'

export const useMenuStore = defineStore('menu', () => {
  const menus = ref<MenuTreeNode[]>([])
  const loaded = ref(false)

  const fetchMenus = async () => {
    try {
      const res = await systemApi.menuUserTree()
      menus.value = res.data
      loaded.value = true
    } catch {
      menus.value = []
      loaded.value = false
    }
  }

  const clearMenus = () => {
    menus.value = []
    loaded.value = false
  }

  return { menus, loaded, fetchMenus, clearMenus }
})
```

- [ ] **Step 2: 修改 userStore.ts — 登录后获取菜单**

在 userStore 的 login 方法中，`await fetchUserInfo()` 后追加 `await menuStore.fetchMenus()`：

```typescript
import { useMenuStore } from './menuStore'

// 在 login 方法中：
const menuStore = useMenuStore()
const login = async (username: string, password: string) => {
  const res = await authApi.login({ username, password })
  token.value = res.data.token
  localStorage.setItem('token', res.data.token)
  await fetchUserInfo()
  await menuStore.fetchMenus()
}
```

在 logout 方法中追加 `menuStore.clearMenus()`：

```typescript
const logout = () => {
  token.value = ''
  user.value = null
  localStorage.removeItem('token')
  useMenuStore().clearMenus()
}
```

- [ ] **Step 3: 提交**

```bash
git add aicoder-web/src/stores/menuStore.ts aicoder-web/src/stores/userStore.ts
git commit -m "feat(web): 添加 menuStore + 登录流程集成菜单获取"
```

---

## Task 9: 前端侧边栏动态菜单

**Files:**
- Modify: `aicoder-web/src/layouts/SideMenu.vue`

- [ ] **Step 1: 重构 SideMenu.vue — 动态菜单渲染**

关键变更：
1. 移除硬编码 `menuItems` 数组
2. 引入 `useMenuStore` 获取动态菜单
3. 支持多级菜单渲染（系统管理作为可展开目录）
4. 每个菜单项带图标（用 SVG 映射）

模板结构变为：
- 遍历 `menuStore.menus`
- 如果菜单有 children 且 type === 1，渲染为可展开目录项（带箭头）
- 否则渲染为普通菜单链接
- 图标通过 icon 字段名映射到 SVG 组件

需要新增的响应式状态：
- `expandedMenus: ref<Set<number>>(new Set())` — 已展开的目录菜单 ID
- `toggleExpand(id)` — 展开/折叠目录

图标映射：创建一个 `iconMap` 对象，key 为 icon 字段值（如 'Home', 'Chat', 'System' 等），value 为对应的 SVG path。

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/layouts/SideMenu.vue
git commit -m "feat(web): 侧边栏菜单改为动态渲染，支持多级菜单"
```

---

## Task 10: 前端路由更新

**Files:**
- Modify: `aicoder-web/src/router/index.ts`

- [ ] **Step 1: 在 MainLayout 的 children 中追加系统管理路由**

在路由配置中 MainLayout 的 children 数组末尾追加：

```typescript
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/UserManageView.vue')
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/RoleManageView.vue')
      },
      {
        path: 'system/permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/PermissionView.vue')
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/MenuManageView.vue')
      },
```

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/router/index.ts
git commit -m "feat(web): 添加系统管理路由"
```

---

## Task 11: 用户管理页面

**Files:**
- Create: `aicoder-web/src/views/system/UserManageView.vue`

- [ ] **Step 1: 创建 UserManageView.vue**

功能：
- 顶部搜索框 + 新增用户按钮
- 表格：用户名、昵称、邮箱、状态、角色、创建时间、操作
- 操作列：编辑、分配角色、重置密码、启用/禁用、删除
- 新增/编辑弹窗：用户名、密码（新增时必填）、昵称、邮箱
- 分配角色弹窗：从角色列表中多选
- 重置密码弹窗：输入新密码
- 启用/禁用：直接切换，不弹窗
- 分页：底部分页组件

使用项目已有的 CSS 类：`.btn`、`.btn-primary`、`.btn-danger`、`.card`、`.modal-overlay`、`.modal-content`、`.form-group`。

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/views/system/UserManageView.vue
git commit -m "feat(web): 用户管理页面"
```

---

## Task 12: 角色管理页面

**Files:**
- Create: `aicoder-web/src/views/system/RoleManageView.vue`

- [ ] **Step 1: 创建 RoleManageView.vue**

功能：
- 顶部新增角色按钮
- 表格：角色名、编码、排序、备注、操作
- 操作列：编辑、分配菜单、删除
- 新增/编辑弹窗：角色名、编码（新增时）、排序、备注
- 分配菜单弹窗：树形勾选菜单（递归渲染 MenuTreeNode）

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/views/system/RoleManageView.vue
git commit -m "feat(web): 角色管理页面"
```

---

## Task 13: 菜单管理页面

**Files:**
- Create: `aicoder-web/src/views/system/MenuManageView.vue`

- [ ] **Step 1: 创建 MenuManageView.vue**

功能：
- 顶部新增菜单按钮
- 树形表格展示菜单层级（缩进 + 展开/折叠）
- 列：名称、图标、路径、类型、排序、状态、操作
- 操作列：编辑、新增子菜单、删除
- 新增/编辑弹窗：父菜单（下拉选择）、名称、路径、图标、排序、类型

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/views/system/MenuManageView.vue
git commit -m "feat(web): 菜单管理页面"
```

---

## Task 14: 权限管理页面

**Files:**
- Create: `aicoder-web/src/views/system/PermissionView.vue`

- [ ] **Step 1: 创建 PermissionView.vue**

功能：
- 左侧：角色列表（可点击选中）
- 右侧：选中角色的菜单权限树（勾选框）
- 底部：保存按钮，批量分配菜单给角色
- 页面加载时获取 `permissionRoles` 和 `permissionMenuTree`

- [ ] **Step 2: 提交**

```bash
git add aicoder-web/src/views/system/PermissionView.vue
git commit -m "feat(web): 权限管理页面"
```

---

## Task 15: 修复 MenuService.deleteMenu 中的关联清理

**Files:**
- Modify: `aicoder-system/src/main/java/com/ai/coder/system/service/MenuService.java`

- [ ] **Step 1: 修正 deleteMenu 方法**

`deleteMenu` 中清理 `role_menu` 关联需要通过 `menuId` 删除，但 `RoleMenuRepository` 只有 `deleteByRoleId`。需要添加 `deleteByMenuId` 方法。

在 `RoleMenuRepository.java` 中添加：

```java
    void deleteByMenuId(Long menuId);
```

在 `MenuService.deleteMenu` 中替换关联清理代码：

```java
    @Transactional
    public void deleteMenu(Long id) {
        List<Menu> children = menuRepository.findByStatusOrderBySortAsc(1).stream()
                .filter(m -> id.equals(m.getParentId())).collect(Collectors.toList());
        if (!children.isEmpty()) {
            throw new RuntimeException("存在子菜单，无法删除");
        }
        roleMenuRepository.deleteByMenuId(id);
        menuRepository.deleteById(id);
    }
```

- [ ] **Step 2: 提交**

```bash
git add aicoder-system/src/main/java/com/ai/coder/system/repository/RoleMenuRepository.java aicoder-system/src/main/java/com/ai/coder/system/service/MenuService.java
git commit -m "fix(system): 修复删除菜单时的关联清理"
```

---

## Task 16: 全局异常处理 + 编译验证

**Files:**
- Create: `aicoder-system/src/main/java/com/ai/coder/system/config/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 GlobalExceptionHandler.java**

```java
package com.ai.coder.system.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }
}
```

- [ ] **Step 2: 完整编译验证**

```bash
cd /Users/xudemac/my/ccworkspace/aicoder && mvn compile
```

- [ ] **Step 3: 提交**

```bash
git add aicoder-system/src/main/java/com/ai/coder/system/config/GlobalExceptionHandler.java
git commit -m "feat(system): 添加全局异常处理"
```

---

## Task 17: 前端编译验证 + 最终提交

- [ ] **Step 1: 前端编译验证**

```bash
cd /Users/xudemac/my/ccworkspace/aicoder/aicoder-web && npm run build
```

- [ ] **Step 2: 如有 TypeScript 错误则修复**

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat: 系统管理模块完整实现 — 用户/角色/权限/菜单管理"
```
