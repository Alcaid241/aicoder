package com.ai.coder.chat.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlQueryTool {

    private final RestTemplate restTemplate;

    public String getName() {
        return "sql_query";
    }

    public String getDescription() {
        return "将自然语言问题转换为 SQL 查询并执行，返回查询结果。当需要查询数据库数据时使用此工具。";
    }

    public String execute(String question, String knowledgeBaseId) {
        log.info("SqlQueryTool 执行: question={}", question);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = """
                    {"knowledgeBaseId": %s, "model": "deepseek-v4-flash", "question": "%s"}
                    """.formatted(knowledgeBaseId != null ? knowledgeBaseId : "0", question);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            String result = restTemplate.postForObject(
                    "http://aicoder-rag/api/rag/sql/ask", entity, String.class);
            return result != null ? result : "SQL 查询无结果";
        } catch (Exception e) {
            log.warn("SQL 查询失败: {}", e.getMessage());
            return "SQL 查询失败: " + e.getMessage();
        }
    }
}
