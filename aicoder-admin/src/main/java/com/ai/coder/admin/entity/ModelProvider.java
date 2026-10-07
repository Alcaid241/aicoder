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
@Table(name = "ai_model_provider")
public class ModelProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String logo;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 500)
    private String baseUrl;

    @Column(length = 500)
    private String apiKey;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer enabled = 1;

    @Column(nullable = false)
    private Integer sort = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
