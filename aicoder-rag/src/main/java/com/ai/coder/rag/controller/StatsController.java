package com.ai.coder.rag.controller;

import com.ai.coder.rag.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class StatsController {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        long count = knowledgeBaseRepository.countByUserId(userId);
        return Map.of("knowledgeBaseCount", count);
    }
}
