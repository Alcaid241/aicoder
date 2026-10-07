package com.ai.coder.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class SqlAskRequest {
    private Long knowledgeBaseId;
    private String model;
    private String question;
    private List<HistoryItem> history;

    @Data
    public static class HistoryItem {
        private String role;
        private String content;
    }
}
