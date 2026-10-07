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
