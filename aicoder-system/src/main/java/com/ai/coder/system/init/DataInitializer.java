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
        // 先创建一级菜单（目录和独立菜单）
        Menu home = saveMenu(null, "首页", "/home", "Home", 1, 2);
        Menu chat = saveMenu(null, "对话", "/chat", "Chat", 2, 2);
        Menu knowledgeMgmt = saveMenu(null, "知识库管理", "/knowledge-mgmt", "KnowledgeMgmt", 3, 1);
        Menu workflow = saveMenu(null, "工作流", "/workflow", "Workflow", 4, 2);
        Menu modelMgmt = saveMenu(null, "模型管理", "/model", "ModelMgmt", 5, 1);
        Menu profile = saveMenu(null, "个人信息", "/profile", "Profile", 6, 2);
        Menu system = saveMenu(null, "系统管理", "/system", "System", 7, 1);
        Menu skillMgmt = saveMenu(null, "技能管理", "/skill", "Skill", 8, 2);

        // 再创建二级菜单（知识库管理子菜单）
        Menu knowledge = saveMenu(knowledgeMgmt.getId(), "知识库", "/knowledge", "Knowledge", 1, 2);
        Menu rag = saveMenu(knowledgeMgmt.getId(), "RAG 对话", "/rag", "Rag", 2, 2);
        Menu sql = saveMenu(knowledgeMgmt.getId(), "NL2SQL", "/sql", "Sql", 3, 2);

        // 二级菜单（模型管理子菜单）
        Menu providerConfig = saveMenu(modelMgmt.getId(), "厂商配置", "/model/provider", "Provider", 1, 2);
        Menu modelConfig = saveMenu(modelMgmt.getId(), "模型配置", "/model/config", "ModelConfig", 2, 2);
        Menu vectordbConfig = saveMenu(modelMgmt.getId(), "向量库配置", "/model/vectordb", "VectorDb", 3, 2);

        // 二级菜单（系统管理子菜单）
        Menu userMgmt = saveMenu(system.getId(), "用户管理", "/system/user", "User", 1, 2);
        Menu roleMgmt = saveMenu(system.getId(), "角色管理", "/system/role", "Role", 2, 2);
        Menu permMgmt = saveMenu(system.getId(), "权限管理", "/system/permission", "Permission", 3, 2);
        Menu menuMgmt = saveMenu(system.getId(), "菜单管理", "/system/menu", "Menu", 4, 2);

        List<Menu> allMenus = List.of(home, chat, knowledgeMgmt, knowledge, rag, sql,
                workflow, modelMgmt, providerConfig, modelConfig, vectordbConfig,
                profile, system, userMgmt, roleMgmt, permMgmt, menuMgmt, skillMgmt);

        Role adminRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        for (Menu menu : allMenus) {
            roleMenuRepository.save(new RoleMenu(adminRole.getId(), menu.getId()));
        }
        log.info("初始化菜单数据，共 {} 条", allMenus.size());
    }

    private void initAdminUser() {
        Role adminRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();

        if (userRepository.findByUsername("admin").isEmpty()) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            com.ai.coder.system.entity.SysUser admin = new com.ai.coder.system.entity.SysUser();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("admin123"));
            admin.setNickname("超级管理员");
            admin.setStatus(1);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());
            userRepository.save(admin);
            log.info("创建管理员账号 admin/admin123");
        }

        // 确保管理员有超级管理员角色
        com.ai.coder.system.entity.SysUser admin = userRepository.findByUsername("admin").orElseThrow();
        List<UserRole> existing = userRoleRepository.findByUserId(admin.getId());
        if (existing.stream().noneMatch(ur -> ur.getRoleId().equals(adminRole.getId()))) {
            userRoleRepository.save(new UserRole(admin.getId(), adminRole.getId()));
            log.info("为管理员分配超级管理员角色");
        }
    }

    private Menu saveMenu(Long parentId, String name, String path, String icon, int sort, int type) {
        Menu m = new Menu();
        m.setParentId(parentId);
        m.setName(name);
        m.setPath(path);
        m.setIcon(icon);
        m.setSort(sort);
        m.setStatus(1);
        m.setType(type);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        return menuRepository.save(m);
    }
}
