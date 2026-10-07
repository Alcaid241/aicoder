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
