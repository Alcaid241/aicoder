package com.ai.coder.workflow.service;

import com.ai.coder.workflow.config.DynamicVectorStoreConfig;
import com.ai.coder.workflow.node.ConditionNode;
import com.ai.coder.workflow.node.LlmNode;
import com.ai.coder.workflow.node.LoopNode;
import com.ai.coder.workflow.node.Nl2SqlNode;
import com.ai.coder.workflow.node.RagNode;
import com.ai.coder.workflow.node.SubWorkflowNode;
import com.ai.coder.workflow.node.ToolNode;
import com.ai.coder.workflow.registry.DynamicModelRegistry;
import com.ai.coder.workflow.repository.WorkflowRepository;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WorkflowNodeFactory {

    private final DynamicModelRegistry modelRegistry;
    private final DynamicVectorStoreConfig dynamicVectorStoreConfig;
    private final ObjectMapper objectMapper;
    private final WorkflowSqlService sqlService;
    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionService executionService;

    public WorkflowNodeFactory(DynamicModelRegistry modelRegistry,
                               DynamicVectorStoreConfig dynamicVectorStoreConfig,
                               ObjectMapper objectMapper,
                               WorkflowSqlService sqlService,
                               WorkflowRepository workflowRepository,
                               @Lazy WorkflowExecutionService executionService) {
        this.modelRegistry = modelRegistry;
        this.dynamicVectorStoreConfig = dynamicVectorStoreConfig;
        this.objectMapper = objectMapper;
        this.sqlService = sqlService;
        this.workflowRepository = workflowRepository;
        this.executionService = executionService;
    }

    public NodeAction createNode(JsonNode nodeData) {
        String type = nodeData.path("type").asText();
        JsonNode config = nodeData.path("data").path("config");

        return switch (type) {
            case "llm" -> createLlmNode(config);
            case "rag" -> createRagNode(config);
            case "nl2sql" -> createNl2SqlNode(config);
            case "condition" -> createConditionNode(config);
            case "tool" -> createToolNode(config);
            case "subworkflow" -> createSubWorkflowNode(config);
            case "loop" -> createLoopNode(config);
            default -> throw new IllegalArgumentException("不支持的节点类型: " + type);
        };
    }

    private LlmNode createLlmNode(JsonNode config) {
        String model = config.path("model").asText("deepseek-v4-flash");
        String promptTemplate = config.path("promptTemplate").asText("请回答以下问题：{{input}}");
        String inputKey = config.path("inputKey").asText("query");
        String outputKey = config.path("outputKey").asText("result");

        ChatModel chatModel = modelRegistry.getChatModel(model);
        if (chatModel == null) {
            throw new IllegalArgumentException("不支持的模型: " + model);
        }

        return new LlmNode(chatModel, promptTemplate, inputKey, outputKey);
    }

    private RagNode createRagNode(JsonNode config) {
        String knowledgeBaseId = config.path("knowledgeBaseId").asText();
        int topK = config.path("topK").asInt(5);
        String inputKey = config.path("inputKey").asText("query");
        String outputKey = config.path("outputKey").asText("context");

        return new RagNode(
                dynamicVectorStoreConfig.getVectorStore(),
                knowledgeBaseId, topK, inputKey, outputKey
        );
    }

    private Nl2SqlNode createNl2SqlNode(JsonNode config) {
        String knowledgeBaseId = config.path("knowledgeBaseId").asText();
        String model = config.path("model").asText("deepseek-v4-flash");
        String databaseName = config.path("databaseName").asText("");
        String inputKey = config.path("inputKey").asText("query");
        String outputKey = config.path("outputKey").asText("sqlResult");

        return new Nl2SqlNode(sqlService, knowledgeBaseId, model, databaseName, inputKey, outputKey);
    }

    private ConditionNode createConditionNode(JsonNode config) {
        String inputKey = config.path("inputKey").asText("result");
        List<Map<String, String>> conditions = new ArrayList<>();
        JsonNode conditionsNode = config.path("conditions");
        if (conditionsNode.isArray()) {
            for (JsonNode cond : conditionsNode) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("label", cond.path("label").asText());
                entry.put("match", cond.path("match").asText());
                conditions.add(entry);
            }
        }
        return new ConditionNode(inputKey, conditions);
    }

    private ToolNode createToolNode(JsonNode config) {
        String url = config.path("url").asText();
        String method = config.path("method").asText("GET");
        String bodyTemplate = config.path("bodyTemplate").asText("");

        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode headersNode = config.path("headers");
        if (headersNode.isObject()) {
            headersNode.fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
        }

        String inputKey = config.path("inputKey").asText("query");
        String outputKey = config.path("outputKey").asText("httpResult");

        return new ToolNode(url, method, headers, bodyTemplate, inputKey, outputKey);
    }

    private SubWorkflowNode createSubWorkflowNode(JsonNode config) {
        Long workflowId = config.path("workflowId").asLong();
        String inputKey = config.path("inputKey").asText("query");
        String outputKey = config.path("outputKey").asText("subResult");
        return new SubWorkflowNode(workflowRepository, executionService, workflowId, inputKey, outputKey);
    }

    private LoopNode createLoopNode(JsonNode config) {
        int maxIterations = config.path("maxIterations").asInt(5);
        String exitCondition = config.path("exitCondition").asText("");
        String inputKey = config.path("inputKey").asText("query");
        String outputKey = config.path("outputKey").asText("loopResult");
        return new LoopNode(maxIterations, exitCondition, inputKey, outputKey);
    }
}
