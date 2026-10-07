package com.ai.coder.workflow.service;

import com.ai.coder.workflow.config.DynamicVectorStoreConfig;
import com.ai.coder.workflow.model.entity.KnowledgeBase;
import com.ai.coder.workflow.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSqlService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DynamicVectorStoreConfig dynamicVectorStoreConfig;
    private final Map<String, ChatModel> modelMap;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private static final String SQL_SYSTEM_PROMPT = """
            你是一个专业的 SQL 生成助手。根据提供的数据库 DDL 信息，将用户的自然语言问题转换为 SQL 查询语句。

            规则：
            1. 只生成 SELECT 查询语句，严禁生成 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE 等修改数据的语句。
            2. 如果用户的问题存在歧义，请设置 "clarification" 字段说明歧义内容。
            3. 如果用户的问题与数据库查询无关、涉及敏感操作、或无法根据 DDL 生成合理的 SQL，请设置 "isRejected" 为 true。
            4. 生成的 SQL 必须符合 MySQL 语法规范。
            5. 请严格按以下 JSON 格式返回，不要包含任何其他内容：
            {
              "sql": "生成的SQL语句，如果没有则留空",
              "clarification": "歧义说明或拒绝原因，如果没有则留空",
              "isRejected": false,
              "canExecute": true
            }

            参考的数据库 DDL 结构：
            %s
            """;

    public String generateAndExecute(String question, String knowledgeBaseId, String modelName,
                                      String databaseName) throws Exception {
        KnowledgeBase kb = knowledgeBaseRepository.findById(Long.parseLong(knowledgeBaseId))
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + knowledgeBaseId));

        if (!"DDL".equals(kb.getType())) {
            throw new IllegalArgumentException("只有 DDL 类型的知识库支持 SQL 生成");
        }

        VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore();
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question).topK(10)
                        .filterExpression("knowledgeBaseId == '" + knowledgeBaseId + "'")
                        .build()
        );

        String ddlContext = docs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        String systemPrompt = String.format(SQL_SYSTEM_PROMPT, ddlContext);

        ChatModel chatModel = modelMap.get(modelName);
        if (chatModel == null) chatModel = modelMap.values().iterator().next();

        String responseText = chatModel.call(
                new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(question)))
        ).getResult().getOutput().getText();

        String sql = parseSql(responseText);
        if (sql == null || sql.isBlank()) {
            throw new RuntimeException("无法生成有效的 SQL 语句");
        }

        log.info("NL2SQL 生成 SQL: {}", sql);
        return executeSql(sql, databaseName);
    }

    private String parseSql(String responseText) {
        try {
            String json = responseText.trim();
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                json = json.substring(0, json.indexOf("```"));
            }
            json = json.trim();
            var node = objectMapper.readTree(json);
            if (node.has("isRejected") && node.get("isRejected").asBoolean()) {
                throw new RuntimeException("SQL 生成被拒绝: " + node.path("clarification").asText());
            }
            return node.path("sql").asText("");
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            log.warn("解析 SQL 响应失败: {}", e.getMessage());
            return responseText.trim();
        }
    }

    private String executeSql(String sql, String databaseName) throws Exception {
        String normalized = sql.trim().toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("SHOW")
                && !normalized.startsWith("DESCRIBE") && !normalized.startsWith("EXPLAIN")) {
            throw new IllegalArgumentException("只允许执行 SELECT 查询语句");
        }

        try (Connection conn = dataSource.getConnection()) {
            if (databaseName != null && !databaseName.isBlank()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("USE " + databaseName);
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                List<Map<String, String>> rows = new ArrayList<>();
                int count = 0;
                while (rs.next() && count < 200) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        row.put(columns.get(i - 1), val != null ? val.toString() : null);
                    }
                    rows.add(row);
                    count++;
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("sql", sql);
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("totalRows", count);
                return objectMapper.writeValueAsString(result);
            }
        }
    }
}
