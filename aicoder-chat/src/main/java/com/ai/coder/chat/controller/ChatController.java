package com.ai.coder.chat.controller;

import com.ai.coder.chat.dto.ChatRequest;
import com.ai.coder.chat.dto.ChatResponse;
import com.ai.coder.chat.dto.ConversationDTO;
import com.ai.coder.chat.dto.MessageDTO;
import com.ai.coder.chat.service.ChatService;
import com.ai.coder.chat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;

    @PostMapping("/send")
    public ChatResponse send(@RequestBody ChatRequest request,
                             @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return chatService.chat(request, userId);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request,
                                                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return chatService.chatStream(request, userId);
    }

    @GetMapping("/conversations")
    public List<ConversationDTO> conversations(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return conversationService.getConversations(userId);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDTO> messages(@PathVariable Long id) {
        return chatService.getMessages(id);
    }

    @DeleteMapping("/conversations/{id}")
    public void deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
    }
}
