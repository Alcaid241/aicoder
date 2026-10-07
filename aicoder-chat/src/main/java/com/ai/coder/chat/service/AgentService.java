package com.ai.coder.chat.service;

import com.ai.coder.chat.dto.AgentRequest;
import com.ai.coder.chat.entity.ChatMessage;
import com.ai.coder.chat.registry.DynamicModelRegistry;
import com.ai.coder.chat.repository.ChatMessageRepository;
import com.ai.coder.chat.bridge.McpToolBridge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final DynamicModelRegistry modelRegistry;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationService conversationService;
    private final McpToolBridge mcpToolBridge;
    private final ObjectMapper objectMapper;

    private static final String AGENT_PROMPT_TEMPLATE = """
            你是一个智能 AI Agent，可以使用工具来完成任务。

            可用工具：
            %s

            当需要使用工具时，请严格按以下 JSON 格式回复（不要包含其他文字）：
            {"tool": "工具名", "args": {"参数名": "参数值"}, "thought": "为什么要调用这个工具"}

            当不需要使用工具时，直接用自然语言回复用户。

            每次最多调用一个工具。调用后你会收到工具结果，再决定下一步。
            """;

    private static final int MAX_ITERATIONS = 5;

    public Flux<ServerSentEvent<String>> agentStream(AgentRequest request, Long userId) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().limit(Integer.MAX_VALUE);

        Thread thread = new Thread(() -> {
            try {
                executeAgentLoop(request, userId, sink);
            } catch (Exception e) {
                log.error("Agent 执行失败: {}", e.getMessage(), e);
                try {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("agent_error")
                            .data(objectMapper.writeValueAsString(Map.of("error", e.getMessage())))
                            .build());
                } catch (Exception ignored) {}
                sink.tryEmitComplete();
            }
        });
        thread.start();

        return sink.asFlux();
    }

    private void executeAgentLoop(AgentRequest request, Long userId,
                                   Sinks.Many<ServerSentEvent<String>> sink) throws Exception {
        Long conversationId = request.getConversationId();

        if (conversationId == null) {
            String title = request.getMessage().length() > 30
                    ? request.getMessage().substring(0, 30) + "..."
                    : request.getMessage();
            var conv = conversationService.createConversation(userId, request.getModel(), title, "AGENT");
            conversationId = conv.getId();
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("conversation")
                    .data(objectMapper.writeValueAsString(Map.of("conversationId", conversationId)))
                    .build());
        }

        ChatMessage userMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(request.getMessage())
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemPrompt()));
        for (ChatMessage msg : history) {
            switch (msg.getRole()) {
                case "USER" -> messages.add(new UserMessage(msg.getContent()));
                case "ASSISTANT" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        ChatModel chatModel = modelRegistry.getChatModelMap().get(request.getModel());
        if (chatModel == null) chatModel = modelRegistry.getChatModelMap().values().iterator().next();

        StringBuilder finalAnswer = new StringBuilder();
        String lastToolResult = null;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (lastToolResult != null) {
                messages.add(new UserMessage("工具执行结果：\n" + lastToolResult));
            }

            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();

            JsonNode toolCall = parseToolCall(content);
            if (toolCall != null) {
                String toolName = toolCall.path("tool").asText();
                String thought = toolCall.path("thought").asText("");

                if (!thought.isBlank()) {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("agent_think")
                            .data(objectMapper.writeValueAsString(Map.of("content", thought)))
                            .build());
                }

                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("tool_call")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "tool", toolName,
                                "args", toolCall.path("args"),
                                "thought", thought
                        )))
                        .build());

                String toolResult = executeTool(toolName, toolCall.path("args"));

                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("tool_result")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "tool", toolName,
                                "result", toolResult
                        )))
                        .build());

                messages.add(new AssistantMessage(content));
                lastToolResult = toolResult;
            } else {
                finalAnswer.append(content);
                break;
            }
        }

        if (finalAnswer.isEmpty() && lastToolResult != null) {
            messages.add(new UserMessage("请根据以上工具执行结果，给出最终回答。"));
            Prompt summaryPrompt = new Prompt(messages);
            ChatResponse summaryResponse = chatModel.call(summaryPrompt);
            finalAnswer.append(summaryResponse.getResult().getOutput().getText());

            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("agent_answer")
                    .data(objectMapper.writeValueAsString(Map.of("content", finalAnswer.toString())))
                    .build());
        } else {
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("agent_answer")
                    .data(objectMapper.writeValueAsString(Map.of("content", finalAnswer.toString())))
                    .build());
        }

        ChatMessage assistantMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content(finalAnswer.toString())
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("done")
                .data("{}")
                .build());
        sink.tryEmitComplete();
    }

    /** 动态构建系统提示：从 mcp 拉取工具列表 */
    private String buildSystemPrompt() {
        StringBuilder tools = new StringBuilder();
        List<Map<String, Object>> mcpTools = mcpToolBridge.listTools();
        if (mcpTools.isEmpty()) {
            tools.append("（暂无可用工具，mcp 服务可能未启动）");
        } else {
            for (int i = 0; i < mcpTools.size(); i++) {
                Map<String, Object> t = mcpTools.get(i);
                String name = String.valueOf(t.getOrDefault("name", "?"));
                String desc = String.valueOf(t.getOrDefault("description", ""));
                if (desc.length() > 80) desc = desc.substring(0, 77) + "...";
                tools.append(String.format("%d. %s - %s\n", i + 1, name, desc));
            }
        }
        return String.format(AGENT_PROMPT_TEMPLATE, tools.toString().stripTrailing());
    }

    private JsonNode parseToolCall(String content) {
        try {
            String json = content.trim();
            if (json.contains("```json")) {
                int start = json.indexOf("```json") + 7;
                int end = json.indexOf("```", start);
                if (end > start) json = json.substring(start, end).trim();
            } else if (json.contains("```")) {
                int start = json.indexOf("```") + 3;
                int end = json.indexOf("```", start);
                if (end > start) json = json.substring(start, end).trim();
            }

            JsonNode node = objectMapper.readTree(json);
            if (node.has("tool") && !node.path("tool").asText().isEmpty()) {
                return node;
            }
        } catch (Exception e) {
            // 不是 JSON，不是工具调用
        }
        return null;
    }

    /** 全部工具统一走 MCP 桥接调用 */
    private String executeTool(String toolName, JsonNode args) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> argMap = objectMapper.convertValue(args, Map.class);
            return mcpToolBridge.callTool(toolName, argMap);
        } catch (Exception e) {
            log.warn("MCP 工具 {} 调用失败: {}", toolName, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }
}
