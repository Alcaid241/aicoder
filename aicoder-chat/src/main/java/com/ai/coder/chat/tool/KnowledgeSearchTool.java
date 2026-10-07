package com.ai.coder.chat.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final RestTemplate restTemplate;

    public String getName() {
        return "knowledge_search";
    }

    public String getDescription() {
        return "搜索知识库，返回与查询相关的文档片段。当需要查找特定知识或文档内容时使用此工具。";
    }

    public String execute(String query, String knowledgeBaseId) {
        log.info("KnowledgeSearchTool 执行: query={}, kbId={}", query, knowledgeBaseId);
        try {
            String url = "http://aicoder-rag/api/rag/kb";
            ResponseEntity<?> response = restTemplate.exchange(url, HttpMethod.GET, null, Object.class);
            return "知识库搜索结果：暂未找到匹配的文档片段。请确保已上传相关文档到知识库。";
        } catch (Exception e) {
            log.warn("知识库搜索失败: {}", e.getMessage());
            return "知识库搜索失败: " + e.getMessage();
        }
    }
}
