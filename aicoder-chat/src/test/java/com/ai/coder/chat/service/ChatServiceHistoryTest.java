package com.ai.coder.chat.service;

import com.ai.coder.chat.entity.ChatMessage;
import com.ai.coder.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link ChatService#historyMessages(Long, Long)} 的去重与清理：
 * 1. 排除刚保存的"当前 user 消息"（该消息由 ChatClient 的 {@code .user()} 提供，否则重复一次）。
 * 2. 不再回灌 SYSTEM 行（系统提示统一由 {@code .system()} 提供，SYSTEM 分支为死代码已移除）。
 * 3. 保留历史 USER/ASSISTANT 轮次。
 * <p>ChatService 的其它依赖（modelRegistry/conversationService/advisor/tool）在此用例不参与，传 null。
 */
class ChatServiceHistoryTest {

    @Test
    void historyMessages_excludes_current_user_message_and_drops_system_rows() {
        Long convId = 1L;
        Long currentUserMsgId = 10L;

        List<ChatMessage> dbRows = List.of(
                row(1L, "USER", "你好"),
                row(2L, "SYSTEM", "遗留系统提示"),
                row(3L, "ASSISTANT", "你好，有什么可以帮你？"),
                row(currentUserMsgId, "USER", "帮我读 greeting-skill"));

        ChatMessageRepository repo = mock(ChatMessageRepository.class);
        when(repo.findByConversationIdOrderByCreatedAtAsc(convId)).thenReturn(dbRows);

        ChatService service = new ChatService(null, null, repo, null, null, null, null);
        List<Message> messages = service.historyMessages(convId, currentUserMsgId);

        // 期望：仅保留 1 条 USER（你好）+ 1 条 ASSISTANT；当前 user（帮我读...）被排除；SYSTEM 被丢弃。
        assertEquals(2, messages.size(), "应排除当前 user 消息并丢弃 SYSTEM 行");
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("你好", messages.get(0).getText());
        assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("你好，有什么可以帮你？", messages.get(1).getText());
    }

    private static ChatMessage row(Long id, String role, String content) {
        return ChatMessage.builder()
                .id(id)
                .conversationId(1L)
                .role(role)
                .content(content)
                .build();
    }
}
