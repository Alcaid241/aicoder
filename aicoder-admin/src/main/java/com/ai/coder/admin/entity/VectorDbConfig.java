package com.ai.coder.admin.entity;

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
@Table(name = "ai_vector_db_config")
public class VectorDbConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String dbType;

    private String host;

    private Integer port;

    private String databaseName;

    private String collectionName;

    private String extraConfig;

    private Boolean active;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
