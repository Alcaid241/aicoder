package com.ai.coder.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_user_role")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    private Long userId;

    @Id
    private Long roleId;
}
