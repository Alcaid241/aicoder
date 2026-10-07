package com.ai.coder.rag.dto;

import lombok.Data;

@Data
public class RagChatRequest {

    private Long knowledgeBaseId;

    private String model;

    private String message;

    private String conversationId;
}
