package com.ai.coder.chat.service;

import com.ai.coder.chat.dto.ChatRequest;
import com.ai.coder.chat.dto.ChatResponse;
import com.ai.coder.chat.dto.MessageDTO;
import com.ai.coder.chat.entity.ChatMessage;
import com.ai.coder.chat.entity.Conversation;
import com.ai.coder.chat.registry.DynamicModelRegistry;
import com.ai.coder.chat.repository.ChatMessageRepository;
import com.ai.coder.chat.tool.ReadSkillTool;
import com.ai.coder.chat.tool.SubmitSkillDraftTool;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final DynamicModelRegistry modelRegistry;
    private final ConversationService conversationService;
    private final ChatMessageRepository chatMessageRepository;
    private final SkillPromptAugmentAdvisor skillAdvisor;
    private final ReadSkillTool readSkillTool;
    private final SubmitSkillDraftTool submitSkillDraftTool;
    private final ModelConfigRepository modelConfigRepository;

    public ChatModel resolveModel(String modelId) {
        return modelRegistry.getChatModel(modelId);
    }

    /**
     * 构建一个 per-request 的 ChatClient（复用单例 advisor/tool bean；按请求解析模型）。
     * 挂载技能目录增强 advisor（将 ACTIVE 技能目录注入系统提示）+ read_skill 工具（按需加载完整 SKILL.md）。
     */
    private ChatClient buildSkillClient(String modelId) {
        ChatModel chatModel = resolveModel(modelId);
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(skillAdvisor);
        // 技能工具（read_skill/submit_skill_draft）需模型支持 function-calling。
        // 能力取决于具体模型本身，不按 provider 类型一刀切——由管理员在模型配置中指定。
        boolean supportsTools = supportsToolCalling(modelId);
        if (supportsTools) {
            builder.defaultTools(readSkillTool, submitSkillDraftTool);
        }
        return builder.build();
    }

    /** 查模型的 support_tools 标记。Ollama 等 provider 的 disableThinking 选项也在此处设置。 */
    private boolean supportsToolCalling(String modelId) {
        return modelConfigRepository.findByModelCodeAndEnabled(modelId, 1)
                .map(c -> c.getSupportTools() != null && c.getSupportTools() == 1)
                .orElse(false);
    }

    /**
     * 抽取历史消息组装逻辑（不含 system prompt 与当前 user message，那些由 ChatClient 调用时指定）。
     *
     * @param excludeMessageId 刚保存的"当前 user 消息" id，需从历史中排除——它由 ChatClient 的
     *                         {@code .user()} 提供，若再回灌会重复一次（Phase 3 generate 循环会放大此 token 开销）。
     *                         传 {@code null} 时不排除（无当前消息的场景）。
     */
    List<Message> historyMessages(Long conversationId, Long excludeMessageId) {
        List<Message> messages = new ArrayList<>();
        if (conversationId != null) {
            List<ChatMessage> history = chatMessageRepository
                    .findByConversationIdOrderByCreatedAtAsc(conversationId);
            for (ChatMessage msg : history) {
                if (excludeMessageId != null && excludeMessageId.equals(msg.getId())) {
                    continue; // 跳过当前 user 消息（由 .user() 提供）
                }
                switch (msg.getRole()) {
                    // SYSTEM 不回灌：系统提示统一由 .system() 提供，历史 SYSTEM 行为遗留数据，丢弃。
                    case "USER" -> messages.add(new UserMessage(msg.getContent()));
                    case "ASSISTANT" -> messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        return messages;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, Long userId) {
        Long conversationId = request.getConversationId();
        Conversation conversation;

        if (conversationId == null) {
            String title = request.getMessage().length() > 30
                    ? request.getMessage().substring(0, 30) + "..."
                    : request.getMessage();
            conversation = conversationService.createConversation(userId, request.getModel(), title);
            conversationId = conversation.getId();
        }

        ChatMessage userMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(request.getMessage())
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        String responseContent = buildSkillClient(request.getModel())
                .prompt()
                .toolContext(Map.<String, Object>of("userId", userId))
                .system("你是一个专业的AI编程助手，请用中文回答问题。")
                .messages(historyMessages(conversationId, userMsg.getId()))
                .user(request.getMessage())
                .call()
                .content();

        ChatMessage assistantMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content(responseContent)
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        return ChatResponse.builder()
                .content(responseContent)
                .model(request.getModel())
                .conversationId(conversationId)
                .role("ASSISTANT")
                .build();
    }

    @Transactional
    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request, Long userId) {
        Long conversationId = request.getConversationId();
        Conversation conversation;

        if (conversationId == null) {
            String title = request.getMessage().length() > 30
                    ? request.getMessage().substring(0, 30) + "..."
                    : request.getMessage();
            conversation = conversationService.createConversation(userId, request.getModel(), title);
            conversationId = conversation.getId();
        }

        ChatMessage userMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(request.getMessage())
                .modelId(request.getModel())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        Flux<String> textFlux = buildSkillClient(request.getModel())
                .prompt()
                .toolContext(Map.<String, Object>of("userId", userId))
                .system("你是一个专业的AI编程助手，请用中文回答问题。")
                .messages(historyMessages(conversationId, userMsg.getId()))
                .user(request.getMessage())
                .stream()
                .content();

        StringBuilder contentBuilder = new StringBuilder();
        final Long convId = conversationId;

        // 先发送一条包含 conversationId 的事件
        ServerSentEvent<String> initEvent = ServerSentEvent.<String>builder()
                .event("conversation")
                .data("{\"conversationId\":" + convId + "}")
                .build();

        return Flux.concat(
                Flux.just(initEvent),
                textFlux
                        .filter(text -> !text.isEmpty())
                        .map(text -> {
                            contentBuilder.append(text);
                            return ServerSentEvent.<String>builder()
                                    .data(text)
                                    .build();
                        })
                        .doOnComplete(() -> {
                            ChatMessage assistantMsg = ChatMessage.builder()
                                    .conversationId(convId)
                                    .role("ASSISTANT")
                                    .content(contentBuilder.toString())
                                    .modelId(request.getModel())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            chatMessageRepository.save(assistantMsg);
                        })
                        .doOnError(e -> log.error("流式对话异常: {}", e.getMessage()))
        );
    }

    public List<MessageDTO> getMessages(Long conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(m -> MessageDTO.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .modelId(m.getModelId())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
    }
}
