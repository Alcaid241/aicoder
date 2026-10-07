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
@Table(name = "ai_role_menu")
@IdClass(RoleMenuId.class)
public class RoleMenu {

    @Id
    private Long roleId;

    @Id
    private Long menuId;
}
