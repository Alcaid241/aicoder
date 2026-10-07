package com.ai.coder.workflow.service;

import com.ai.coder.workflow.model.entity.WorkflowTemplate;
import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.model.enums.WorkflowStatus;
import com.ai.coder.workflow.repository.WorkflowTemplateRepository;
import com.ai.coder.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowRepository workflowRepository;

    public List<WorkflowTemplate> listTemplates(Long userId) {
        return templateRepository.findByIsSystemOrUserIdOrderByCreatedAtDesc(1, userId);
    }

    public WorkflowTemplate createTemplate(String name, String description, String category,
                                            String graphData, Long userId) {
        WorkflowTemplate template = WorkflowTemplate.builder()
                .name(name)
                .description(description)
                .category(category)
                .graphData(graphData)
                .isSystem(0)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();
        return templateRepository.save(template);
    }

    public WorkflowTemplate createFromWorkflow(Long workflowId, Long userId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + workflowId));
        return createTemplate(workflow.getName() + "（模板）", workflow.getDescription(),
                "GENERAL", workflow.getGraphData(), userId);
    }

    public Workflow cloneFromTemplate(Long templateId, Long userId) {
        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        Workflow workflow = Workflow.builder()
                .name(template.getName())
                .description(template.getDescription())
                .graphData(template.getGraphData())
                .status(WorkflowStatus.DRAFT)
                .type("WORKFLOW")
                .userId(userId)
                .build();
        return workflowRepository.save(workflow);
    }

    public void deleteTemplate(Long templateId, Long userId) {
        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        if (template.getIsSystem() == 1) {
            throw new IllegalArgumentException("系统模板不可删除");
        }
        if (!userId.equals(template.getUserId())) {
            throw new IllegalArgumentException("只能删除自己创建的模板");
        }
        templateRepository.delete(template);
    }
}
