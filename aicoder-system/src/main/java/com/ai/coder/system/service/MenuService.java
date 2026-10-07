package com.ai.coder.system.service;

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

    public List<MenuTreeNode> getMenuTree() {
        List<Menu> menus = menuRepository.findByStatusOrderBySortAsc(1);
        return buildTree(menus);
    }

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
        List<Menu> children = menuRepository.findByStatusOrderBySortAsc(1).stream()
                .filter(m -> id.equals(m.getParentId())).collect(Collectors.toList());
        if (!children.isEmpty()) {
            throw new RuntimeException("存在子菜单，无法删除");
        }
        roleMenuRepository.deleteByMenuId(id);
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

        List<Menu> children = new ArrayList<>(byParent.getOrDefault(menu.getId(), List.of()));
        children.sort((a, b) -> Integer.compare(a.getSort(), b.getSort()));
        node.setChildren(children.stream().map(c -> toTreeNode(c, byParent)).collect(Collectors.toList()));
        return node;
    }

    private MenuDTO toDTO(Menu menu) {
        return new MenuDTO(menu.getId(), menu.getParentId(), menu.getName(),
                menu.getPath(), menu.getIcon(), menu.getSort(), menu.getStatus(), menu.getType());
    }
}
