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

    @GetMapping("/tree")
    public ResponseEntity<List<MenuTreeNode>> getMenuTree() {
        return ResponseEntity.ok(menuService.getMenuTree());
    }

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
