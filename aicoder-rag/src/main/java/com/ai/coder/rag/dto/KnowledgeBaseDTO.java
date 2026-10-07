package com.ai.coder.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseDTO {

    private Long id;

    private Long userId;

    private String name;

    private String type;

    private String systemPrompt;

    private String databaseName;

    private String jdbcUrl;

    private String dbUsername;

    private String dbPassword;

    private String vectorDbType;

    private String vectorCollection;

    private Long vectorDbConfigId;

    private String description;

    private Integer documentCount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
