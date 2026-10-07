package com.ai.coder.rag.service;

import com.ai.coder.rag.config.DynamicVectorStoreConfig;
import com.ai.coder.rag.dto.RagChatRequest;
import com.ai.coder.rag.entity.KnowledgeBase;
import com.ai.coder.rag.registry.DynamicModelRegistry;
import com.ai.coder.rag.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DynamicVectorStoreConfig dynamicVectorStoreConfig;
    private final DynamicModelRegistry modelRegistry;

    public Flux<String> ragChatStream(RagChatRequest request) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(request.getKnowledgeBaseId())
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + request.getKnowledgeBaseId()));

        ChatModel chatModel = resolveChatModel(request.getModel());

        VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore(kb.getVectorDbType());

        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getMessage())
                        .topK(5)
                        .filterExpression("knowledgeBaseId == '" + request.getKnowledgeBaseId() + "'")
                        .build()
        );

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        List<Message> messages = new ArrayList<>();

        String systemPrompt = kb.getSystemPrompt() != null ? kb.getSystemPrompt() :
                "你是一个智能助手。请根据以下参考资料回答用户的问题。如果参考资料中没有相关信息，请如实告知。";
        systemPrompt += "\n\n参考资料:\n" + context;
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(request.getMessage()));

        Prompt prompt = new Prompt(messages);
        return chatModel.stream(prompt)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        String text = chatResponse.getResult().getOutput().getText();
                        return text != null ? text : "";
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty());
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
