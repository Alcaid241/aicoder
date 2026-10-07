package com.ai.coder.rag.dto;

import lombok.Data;

@Data
public class KnowledgeBaseCreateRequest {

    private String name;

    private String type;

    private String systemPrompt;

    private String databaseName;

    private String jdbcUrl;

    private String dbUsername;

    private String dbPassword;

    private String description;

    private Long vectorDbConfigId;
}
