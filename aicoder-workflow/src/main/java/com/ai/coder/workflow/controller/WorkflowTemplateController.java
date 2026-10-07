package com.ai.coder.workflow.controller;

import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.model.entity.WorkflowTemplate;
import com.ai.coder.workflow.service.WorkflowTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow/templates")
@RequiredArgsConstructor
public class WorkflowTemplateController {

    private final WorkflowTemplateService templateService;

    @GetMapping
    public List<WorkflowTemplate> list(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return templateService.listTemplates(userId);
    }

    @PostMapping
    public WorkflowTemplate create(@RequestBody Map<String, String> body,
                                    @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return templateService.createTemplate(
                body.get("name"), body.get("description"), body.get("category"),
                body.get("graphData"), userId);
    }

    @PostMapping("/{id}/clone")
    public Workflow clone(@PathVariable Long id,
                           @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return templateService.cloneFromTemplate(id, userId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id,
                        @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        templateService.deleteTemplate(id, userId);
    }
}
