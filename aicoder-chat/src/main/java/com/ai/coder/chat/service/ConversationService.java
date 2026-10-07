package com.ai.coder.chat.service;

import com.ai.coder.chat.dto.ConversationDTO;
import com.ai.coder.chat.entity.ChatMessage;
import com.ai.coder.chat.entity.Conversation;
import com.ai.coder.chat.repository.ChatMessageRepository;
import com.ai.coder.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public Conversation createConversation(Long userId, String modelId, String title) {
        return createConversation(userId, modelId, title, "CHAT");
    }

    @Transactional
    public Conversation createConversation(Long userId, String modelId, String title, String type) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .modelId(modelId)
                .title(title)
                .type(type)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void updateTitle(Long conversationId, String title) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setTitle(title);
            c.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(c);
        });
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .forEach(m -> chatMessageRepository.delete(m));
        conversationRepository.deleteById(conversationId);
    }

    public List<ConversationDTO> getConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(c -> ConversationDTO.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .modelId(c.getModelId())
                        .type(c.getType())
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }

    public List<ChatMessage> getMessages(Long conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
