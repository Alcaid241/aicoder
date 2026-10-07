package com.ai.coder.chat.controller;

import com.ai.coder.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class StatsController {

    private final ConversationRepository conversationRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        long count = conversationRepository.countByUserId(userId);
        return Map.of("conversationCount", count);
    }
}
