package com.ai.coder.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_model_config")
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String modelCode;

    @Column(nullable = false, length = 20)
    private String modelType;

    @Column(nullable = false)
    private Integer enabled = 1;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Integer supportTools = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
