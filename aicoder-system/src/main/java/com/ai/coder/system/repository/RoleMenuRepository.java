package com.ai.coder.system.repository;

import com.ai.coder.system.entity.RoleMenu;
import com.ai.coder.system.entity.RoleMenuId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, RoleMenuId> {

    List<RoleMenu> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);

    void deleteByMenuId(Long menuId);
}
