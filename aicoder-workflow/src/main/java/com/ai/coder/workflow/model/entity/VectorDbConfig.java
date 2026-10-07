package com.ai.coder.workflow.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_vector_db_config")
public class VectorDbConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "db_type", length = 20)
    private String dbType;

    private String host;

    private Integer port;

    private String username;

    @Column(columnDefinition = "TEXT")
    private String password;

    @Column(name = "database_name")
    private String databaseName;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Boolean active = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
