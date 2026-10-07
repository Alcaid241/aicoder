package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RestTemplate restTemplate;

    public DashboardStatsResponse getStats(Long userId) {
        long conversationCount = 0;
        long knowledgeBaseCount = 0;
        long workflowCount = 0;
        long executionCount = 0;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "http://aicoder-chat/api/chat/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getBody() != null) {
                conversationCount = ((Number) resp.getBody().getOrDefault("conversationCount", 0)).longValue();
            }
        } catch (Exception e) {
            log.warn("获取对话统计失败(Nacos): {}", e.getMessage());
            try {
                ResponseEntity<Map> resp = new RestTemplate().exchange(
                        "http://localhost:8082/api/chat/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (resp.getBody() != null) {
                    conversationCount = ((Number) resp.getBody().getOrDefault("conversationCount", 0)).longValue();
                }
            } catch (Exception e2) {
                log.warn("获取对话统计失败(direct): {}", e2.getMessage());
            }
        }

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "http://aicoder-rag/api/rag/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getBody() != null) {
                knowledgeBaseCount = ((Number) resp.getBody().getOrDefault("knowledgeBaseCount", 0)).longValue();
            }
        } catch (Exception e) {
            log.warn("获取知识库统计失败(Nacos): {}", e.getMessage());
            try {
                ResponseEntity<Map> resp = new RestTemplate().exchange(
                        "http://localhost:8083/api/rag/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (resp.getBody() != null) {
                    knowledgeBaseCount = ((Number) resp.getBody().getOrDefault("knowledgeBaseCount", 0)).longValue();
                }
            } catch (Exception e2) {
                log.warn("获取知识库统计失败(direct): {}", e2.getMessage());
            }
        }

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "http://aicoder-workflow/api/workflow/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getBody() != null) {
                workflowCount = ((Number) resp.getBody().getOrDefault("workflowCount", 0)).longValue();
                executionCount = ((Number) resp.getBody().getOrDefault("executionCount", 0)).longValue();
            }
        } catch (Exception e) {
            log.warn("获取工作流统计失败(Nacos): {}", e.getMessage());
            try {
                ResponseEntity<Map> resp = new RestTemplate().exchange(
                        "http://localhost:8084/api/workflow/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (resp.getBody() != null) {
                    workflowCount = ((Number) resp.getBody().getOrDefault("workflowCount", 0)).longValue();
                    executionCount = ((Number) resp.getBody().getOrDefault("executionCount", 0)).longValue();
                }
            } catch (Exception e2) {
                log.warn("获取工作流统计失败(direct): {}", e2.getMessage());
            }
        }

        return DashboardStatsResponse.builder()
                .conversationCount(conversationCount)
                .knowledgeBaseCount(knowledgeBaseCount)
                .workflowCount(workflowCount)
                .executionCount(executionCount)
                .build();
    }
}
