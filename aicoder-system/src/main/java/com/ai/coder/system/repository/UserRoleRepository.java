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
