package com.ai.coder.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_knowledge_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String name;

    @Column(length = 10)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    private String databaseName;

    private String vectorDbType;

    private String vectorCollection;

    private Long vectorDbConfigId;

    @Column(length = 500)
    private String jdbcUrl;

    private String dbUsername;

    private String dbPassword;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private Integer documentCount = 0;

    @Builder.Default
    private Integer status = 1;

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
