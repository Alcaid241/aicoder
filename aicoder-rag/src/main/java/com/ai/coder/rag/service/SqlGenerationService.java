package com.ai.coder.rag.service;

import com.ai.coder.rag.config.DynamicVectorStoreConfig;
import com.ai.coder.rag.dto.SqlAskRequest;
import com.ai.coder.rag.dto.SqlAskResponse;
import com.ai.coder.rag.entity.KnowledgeBase;
import com.ai.coder.rag.registry.DynamicModelRegistry;
import com.ai.coder.rag.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGenerationService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DynamicVectorStoreConfig dynamicVectorStoreConfig;
    private final DynamicModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;

    private static final String SQL_SYSTEM_PROMPT = """
            你是一个专业的 NL2SQL 助手。基于提供的数据库 DDL 结构，将用户的自然语言查询转换为准确的 SQL 语句。

            #核心规则
            当且仅当查询完全无歧义时，你才可以直接生成SQL；否则必须优先进行歧义澄清。

            #歧义检测标准
            当用户查询满足以下任一条件时，必须发起澄清：
            - 涉及的表名、列名存在多个可能的匹配
            - 聚合函数（COUNT/SUM/AVG等）的统计对象不明确
            - 时间范围（"最近"、"本月"、"过去"等）没有明确界定
            - 筛选条件（"高"、"低"、"优秀"等）没有量化标准
            - 排序方式（"最新"、"最热"等）没有明确排序字段
            - 多表关联关系不唯一
            - "和/或"逻辑关系不明确
            - "包含/排除"范围不明确

            #澄清问题格式要求
            - 每个澄清问题必须只针对一个歧义点
            - 每个问题必须提供2-3个最可能的答案选项
            - 选项必须互斥且覆盖绝大多数可能的情况
            - 选项必须具体、可执行，避免模糊表述
            - 如果问题允许用户同时选择多个选项（如"需要哪些字段"、"包含哪些条件"），设置multiSelect为true；否则为false

            #多轮澄清规则
            - 如果存在多个独立的歧义点，必须分轮次进行澄清，每轮只问一个问题
            - 上一轮用户选择后，再提出下一个歧义点的问题
            - 不得在一轮中同时提出多个问题
            - 澄清过程中，必须保留用户之前的所有选择信息
            - 只有当所有歧义点都被澄清后，才生成最终的SQL语句

            #最终SQL生成要求
            - 必须严格基于DDL结构和所有澄清选择生成SQL
            - 只生成SELECT查询语句，严禁INSERT/UPDATE/DELETE/DROP/ALTER/CREATE
            - SQL必须符合MySQL语法规范
            - 禁止在IN/ALL/ANY/SOME子查询中使用LIMIT，改用JOIN或CTE(WITH)方式替代
            - SQL应尽量高效，避免不必要的子查询和全表扫描

            #禁止行为
            - 禁止在存在歧义时直接生成SQL
            - 禁止假设用户的意图
            - 禁止使用"可能"、"大概"等不确定的表述生成SQL
            - 禁止在澄清问题中提供超过3个选项
            - 禁止在一轮中提出多个澄清问题
            - 如果用户的问题与数据库查询完全无关，才设置status为REJECTED

            #输出格式
            请严格按以下JSON格式返回，不要包含任何其他内容：

            存在歧义时（单选）：
            {"status":"CLARIFY","clarification":"具体的一个歧义问题","options":["选项A","选项B","选项C"],"multiSelect":false,"sql":null,"isRejected":false,"canExecute":false}

            存在歧义时（多选）：
            {"status":"CLARIFY","clarification":"具体的一个歧义问题","options":["选项A","选项B","选项C"],"multiSelect":true,"sql":null,"isRejected":false,"canExecute":false}

            无歧义、可以直接生成SQL时：
            {"status":"SQL","sql":"SELECT ...","canExecute":true,"clarification":null,"options":null,"multiSelect":null,"isRejected":false}

            查询被拒绝时（与数据库查询完全无关）：
            {"status":"REJECTED","isRejected":true,"clarification":"拒绝原因","sql":null,"options":null,"multiSelect":null,"canExecute":false}

            参考的数据库 DDL 结构：
            %s
            """;

    public SqlAskResponse generateSql(SqlAskRequest request) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(request.getKnowledgeBaseId())
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + request.getKnowledgeBaseId()));

        if (!"DDL".equals(kb.getType())) {
            throw new IllegalArgumentException("只有 DDL 类型的知识库支持 SQL 生成");
        }

        VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore(kb.getVectorDbType());

        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getQuestion())
                        .topK(10)
                        .filterExpression("knowledgeBaseId == '" + request.getKnowledgeBaseId() + "'")
                        .build()
        );

        String ddlContext = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String systemPrompt = String.format(SQL_SYSTEM_PROMPT, ddlContext);

        ChatModel chatModel = resolveChatModel(request.getModel());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        if (request.getHistory() != null) {
            for (SqlAskRequest.HistoryItem item : request.getHistory()) {
                if ("user".equals(item.getRole())) {
                    messages.add(new UserMessage(item.getContent()));
                } else if ("assistant".equals(item.getRole())) {
                    messages.add(new AssistantMessage(item.getContent()));
                }
            }
        }

        messages.add(new UserMessage(request.getQuestion()));

        Prompt prompt = new Prompt(messages);

        ChatResponse chatResponse = chatModel.call(prompt);
        String responseText = chatResponse.getResult().getOutput().getText();

        return parseSqlResponse(responseText);
    }

    private SqlAskResponse parseSqlResponse(String responseText) {
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

            SqlAskResponse response = objectMapper.readValue(json, SqlAskResponse.class);

            if (response.getStatus() == null) {
                if (Boolean.TRUE.equals(response.getIsRejected())) {
                    response.setStatus("REJECTED");
                } else if (response.getSql() != null && !response.getSql().isBlank()) {
                    response.setStatus("SQL");
                } else if (response.getClarification() != null && !response.getClarification().isBlank()) {
                    response.setStatus("CLARIFY");
                } else {
                    response.setStatus("SQL");
                }
            }

            return response;
        } catch (Exception e) {
            log.warn("解析 SQL 响应失败，返回原始文本: {}", e.getMessage());
            return SqlAskResponse.builder()
                    .status("SQL")
                    .sql(responseText)
                    .clarification("")
                    .isRejected(false)
                    .canExecute(true)
                    .build();
        }
    }

    private ChatModel resolveChatModel(String model) {
        if (model == null || model.isBlank()) {
            return modelRegistry.getChatModelMap().values().iterator().next();
        }
        try {
            return modelRegistry.getChatModel(model);
        } catch (IllegalArgumentException e) {
            return modelRegistry.getChatModelMap().values().iterator().next();
        }
    }
}
