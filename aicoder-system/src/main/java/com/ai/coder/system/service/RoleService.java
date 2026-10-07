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
        return roleRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
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
