package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {
    private long conversationCount;
    private long knowledgeBaseCount;
    private long workflowCount;
    private long executionCount;
}
