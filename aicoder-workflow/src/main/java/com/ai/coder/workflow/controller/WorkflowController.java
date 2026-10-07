package com.ai.coder.workflow.controller;

import com.ai.coder.workflow.model.dto.WorkflowCreateRequest;
import com.ai.coder.workflow.model.dto.WorkflowDTO;
import com.ai.coder.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/create")
    public WorkflowDTO create(@Valid @RequestBody WorkflowCreateRequest request,
                              @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return workflowService.create(request, userId);
    }

    @PutMapping("/{id}")
    public WorkflowDTO update(@PathVariable Long id,
                              @Valid @RequestBody WorkflowCreateRequest request) {
        return workflowService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        workflowService.delete(id);
    }

    @GetMapping("/list")
    public List<WorkflowDTO> list(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return workflowService.listByUser(userId);
    }

    @GetMapping("/{id}")
    public WorkflowDTO getById(@PathVariable Long id) {
        return workflowService.getById(id);
    }
}
