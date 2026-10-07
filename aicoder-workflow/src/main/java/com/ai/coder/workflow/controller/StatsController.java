package com.ai.coder.workflow.controller;

import com.ai.coder.workflow.repository.WorkflowExecutionRepository;
import com.ai.coder.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class StatsController {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        long workflowCount = workflowRepository.countByUserId(userId);
        long executionCount = executionRepository.countByUserId(userId);
        return Map.of("workflowCount", workflowCount, "executionCount", executionCount);
    }
}
