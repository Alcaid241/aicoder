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
