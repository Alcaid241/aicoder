package com.ai.coder.system.service;

import com.ai.coder.system.dto.MenuTreeNode;
import com.ai.coder.system.dto.RoleDTO;
import com.ai.coder.system.entity.Role;
import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.repository.RoleMenuRepository;
import com.ai.coder.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public List<MenuTreeNode> getAllMenuTree() {
        return menuService.getMenuTree();
    }
}
