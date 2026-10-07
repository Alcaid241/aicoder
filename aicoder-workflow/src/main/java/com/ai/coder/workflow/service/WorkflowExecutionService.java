package com.ai.coder.workflow.service;

import com.ai.coder.workflow.model.dto.WorkflowExecutionRequest;
import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.model.entity.WorkflowExecution;
import com.ai.coder.workflow.model.entity.WorkflowNodeExecution;
import com.ai.coder.workflow.model.enums.ExecutionStatus;
import com.ai.coder.workflow.repository.WorkflowExecutionRepository;
import com.ai.coder.workflow.repository.WorkflowNodeExecutionRepository;
import com.ai.coder.workflow.repository.WorkflowRepository;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import com.ai.coder.workflow.registry.DynamicModelRegistry;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowNodeExecutionRepository nodeExecutionRepository;
    private final WorkflowNodeFactory nodeFactory;
    private final ObjectMapper objectMapper;
    private final DynamicModelRegistry modelRegistry;

    public Flux<ServerSentEvent<String>> executeWorkflow(Long workflowId, WorkflowExecutionRequest request, Long userId) throws Exception {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + workflowId));

        WorkflowExecution execution = WorkflowExecution.builder()
                .workflowId(workflowId)
                .status(ExecutionStatus.RUNNING)
                .input(request.getInput() != null ? objectMapper.writeValueAsString(request.getInput()) : "{}")
                .userId(userId)
                .build();
        executionRepository.save(execution);

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().limit(Integer.MAX_VALUE);

        Thread thread = new Thread(() -> {
            try {
                executeGraph(workflow, execution, request.getInput(), sink);
            } catch (Exception e) {
                log.error("工作流执行失败: {}", e.getMessage(), e);
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setErrorMessage(e.getMessage());
                execution.setFinishedAt(LocalDateTime.now());
                executionRepository.save(execution);
                try {
                    Map<String, Object> errPayload = new LinkedHashMap<>();
                    errPayload.put("error", e.getMessage() != null ? e.getMessage() : "未知错误");
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("workflow_error").data(objectMapper.writeValueAsString(errPayload)).build());
                } catch (Exception ignored) {}
                sink.tryEmitComplete();
            }
        });
        thread.start();

        return sink.asFlux();
    }

    private void executeGraph(Workflow workflow, WorkflowExecution execution,
                              Map<String, Object> input, Sinks.Many<ServerSentEvent<String>> sink) throws Exception {
        JsonNode graphData = objectMapper.readTree(workflow.getGraphData());
        JsonNode nodes = graphData.path("nodes");
        JsonNode edges = graphData.path("edges");

        // 构建 KeyStrategy
        Set<String> allKeys = new HashSet<>();
        if (input != null) allKeys.addAll(input.keySet());
        for (JsonNode node : nodes) {
            JsonNode config = node.path("data").path("config");
            addIfPresent(allKeys, config, "inputKey");
            addIfPresent(allKeys, config, "outputKey");
        }
        allKeys.add("__route__");
        allKeys.add("__loop_iteration__");

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            for (String key : allKeys) {
                strategies.put(key, new ReplaceStrategy());
            }
            return strategies;
        };

        // 构建 StateGraph
        StateGraph stateGraph = new StateGraph(keyStrategyFactory);

        // 添加节点
        Map<String, String> nodeTypeMap = new HashMap<>();
        for (JsonNode node : nodes) {
            String nodeId = node.path("id").asText();
            String type = node.path("type").asText();
            String label = node.path("data").path("label").asText(nodeId);
            nodeTypeMap.put(nodeId, type);

            if ("start".equals(type) || "end".equals(type)) continue;

            NodeAction nodeAction = nodeFactory.createNode(node);
            stateGraph.addNode(nodeId, AsyncNodeAction.node_async(state -> {
                emitNodeEvent(sink, "node_start", execution.getId(), nodeId, label, null, null);

                WorkflowNodeExecution nodeExec = WorkflowNodeExecution.builder()
                        .executionId(execution.getId())
                        .nodeId(nodeId)
                        .nodeName(label)
                        .nodeType(type.toUpperCase())
                        .status(ExecutionStatus.RUNNING)
                        .startedAt(LocalDateTime.now())
                        .build();
                nodeExecutionRepository.save(nodeExec);

                try {
                    Map<String, Object> result = nodeAction.apply(state);

                    nodeExec.setStatus(ExecutionStatus.COMPLETED);
                    nodeExec.setOutputData(objectMapper.writeValueAsString(result));
                    nodeExec.setFinishedAt(LocalDateTime.now());
                    nodeExecutionRepository.save(nodeExec);

                    emitNodeEvent(sink, "node_complete", execution.getId(), nodeId, label,
                            null, objectMapper.writeValueAsString(result));
                    return result;
                } catch (Exception e) {
                    nodeExec.setStatus(ExecutionStatus.FAILED);
                    nodeExec.setErrorMessage(e.getMessage());
                    nodeExec.setFinishedAt(LocalDateTime.now());
                    nodeExecutionRepository.save(nodeExec);

                    emitNodeEvent(sink, "node_error", execution.getId(), nodeId, label, e.getMessage(), null);
                    throw e;
                }
            }));
        }

        // 收集从 start 节点出发的起始节点
        String startNode = null;
        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();
            if ("start".equals(nodeTypeMap.get(source))) {
                startNode = target;
            }
        }

        if (startNode != null) {
            stateGraph.addEdge(StateGraph.START, startNode);
        }

        // 收集从 condition 节点出发的条件边
        Map<String, Map<String, String>> conditionalEdgesMap = new LinkedHashMap<>();

        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();

            if ("start".equals(nodeTypeMap.get(source))) continue;

            boolean isTargetEnd = "end".equals(nodeTypeMap.get(target));
            String effectiveTarget = isTargetEnd ? StateGraph.END : target;

            if ("condition".equals(nodeTypeMap.get(source))) {
                String sourceHandle = edge.path("sourceHandle").asText("__default__");
                conditionalEdgesMap.computeIfAbsent(source, k -> new LinkedHashMap<>())
                        .put(sourceHandle, effectiveTarget);
            } else if (isTargetEnd) {
                stateGraph.addEdge(source, StateGraph.END);
            } else {
                stateGraph.addEdge(source, target);
            }
        }

        // 添加条件边
        for (Map.Entry<String, Map<String, String>> entry : conditionalEdgesMap.entrySet()) {
            String conditionNodeId = entry.getKey();
            Map<String, String> mappings = entry.getValue();

            stateGraph.addConditionalEdges(conditionNodeId,
                    com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async(state -> {
                        return state.value("__route__", "__default__").toString();
                    }),
                    mappings
            );
        }

        CompiledGraph compiledGraph = stateGraph.compile(CompileConfig.builder().build());

        Map<String, Object> initialState = input != null ? input : new HashMap<>();
        if (!initialState.containsKey("query")) {
            initialState.put("query", "");
        }

        NodeOutput lastOutput = compiledGraph.stream(initialState).blockLast();

        Map<String, Object> stateData = lastOutput != null ? lastOutput.state().data() : new HashMap<>();
        String finalOutput;

        // 对话工作流：用 LLM 根据回复要求生成最终回复
        if ("CHAT".equals(workflow.getType()) && workflow.getReplyRequirements() != null
                && !workflow.getReplyRequirements().isBlank()) {
            String contextForReply = objectMapper.writeValueAsString(stateData);
            String replyPrompt = """
                    你是一个智能助手。根据以下工作流执行结果，按照回复要求生成最终回复。

                    工作流执行结果：
                    %s

                    回复要求：
                    %s

                    请直接输出回复内容，不要包含多余的解释。
                    """.formatted(contextForReply, workflow.getReplyRequirements());

            org.springframework.ai.chat.model.ChatModel chatModel = modelRegistry.getChatModelMap().values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("没有可用的对话模型"));
            String reply = chatModel.call(replyPrompt);
            stateData.put("__reply__", reply);
            finalOutput = reply;
        } else {
            finalOutput = objectMapper.writeValueAsString(stateData);
        }

        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setOutput(finalOutput);
        execution.setFinishedAt(LocalDateTime.now());
        executionRepository.save(execution);

        // 对话工作流发送 reply 事件，普通工作流发送 workflow_complete
        if ("CHAT".equals(workflow.getType()) && stateData.containsKey("__reply__")) {
            Map<String, Object> replyPayload = new LinkedHashMap<>();
            replyPayload.put("reply", stateData.get("__reply__"));
            replyPayload.put("state", stateData);
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("workflow_complete").data(objectMapper.writeValueAsString(replyPayload)).build());
        } else {
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("workflow_complete").data(objectMapper.writeValueAsString(stateData)).build());
        }
        sink.tryEmitComplete();
    }

    public String executeSubWorkflow(Workflow workflow, Map<String, Object> input) throws Exception {
        JsonNode graphData = objectMapper.readTree(workflow.getGraphData());
        JsonNode nodes = graphData.path("nodes");
        JsonNode edges = graphData.path("edges");

        Set<String> allKeys = new HashSet<>();
        if (input != null) allKeys.addAll(input.keySet());
        for (JsonNode node : nodes) {
            JsonNode config = node.path("data").path("config");
            addIfPresent(allKeys, config, "inputKey");
            addIfPresent(allKeys, config, "outputKey");
        }
        allKeys.add("__route__");
        allKeys.add("__loop_iteration__");

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            for (String key : allKeys) {
                strategies.put(key, new ReplaceStrategy());
            }
            return strategies;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory);

        Map<String, String> nodeTypeMap = new HashMap<>();
        for (JsonNode node : nodes) {
            String nodeId = node.path("id").asText();
            String type = node.path("type").asText();
            nodeTypeMap.put(nodeId, type);
            if ("start".equals(type) || "end".equals(type)) continue;
            NodeAction nodeAction = nodeFactory.createNode(node);
            stateGraph.addNode(nodeId, AsyncNodeAction.node_async(state -> nodeAction.apply(state)));
        }

        String startNode = null;
        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();
            if ("start".equals(nodeTypeMap.get(source))) startNode = target;
        }
        if (startNode != null) stateGraph.addEdge(StateGraph.START, startNode);

        Map<String, Map<String, String>> conditionalEdgesMap = new LinkedHashMap<>();
        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();
            if ("start".equals(nodeTypeMap.get(source))) continue;
            boolean isTargetEnd = "end".equals(nodeTypeMap.get(target));
            String effectiveTarget = isTargetEnd ? StateGraph.END : target;
            if ("condition".equals(nodeTypeMap.get(source))) {
                String sourceHandle = edge.path("sourceHandle").asText("__default__");
                conditionalEdgesMap.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(sourceHandle, effectiveTarget);
            } else if (isTargetEnd) {
                stateGraph.addEdge(source, StateGraph.END);
            } else {
                stateGraph.addEdge(source, target);
            }
        }

        for (Map.Entry<String, Map<String, String>> entry : conditionalEdgesMap.entrySet()) {
            stateGraph.addConditionalEdges(entry.getKey(),
                    com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async(state ->
                            state.value("__route__", "__default__").toString()),
                    entry.getValue());
        }

        CompiledGraph compiledGraph = stateGraph.compile(CompileConfig.builder().build());
        NodeOutput lastOutput = compiledGraph.stream(input != null ? input : new HashMap<>()).blockLast();

        if (lastOutput != null) {
            return objectMapper.writeValueAsString(lastOutput.state().data());
        }
        return "{}";
    }

    private void addIfPresent(Set<String> keys, JsonNode config, String field) {
        if (config.has(field) && !config.path(field).asText().isEmpty()) {
            keys.add(config.path(field).asText());
        }
    }

    private void emitNodeEvent(Sinks.Many<ServerSentEvent<String>> sink, String event,
                               Long executionId, String nodeId, String nodeName,
                               String error, String data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("executionId", executionId);
            payload.put("nodeId", nodeId);
            payload.put("nodeName", nodeName);
            if (error != null) payload.put("error", error);
            if (data != null) payload.put("data", data);

            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event(event)
                    .data(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("发送 SSE 事件失败: {}", e.getMessage());
        }
    }
}
