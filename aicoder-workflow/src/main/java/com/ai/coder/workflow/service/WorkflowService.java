package com.ai.coder.workflow.service;

import com.ai.coder.workflow.model.dto.WorkflowCreateRequest;
import com.ai.coder.workflow.model.dto.WorkflowDTO;
import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.model.enums.WorkflowStatus;
import com.ai.coder.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;

    @Transactional
    public WorkflowDTO create(WorkflowCreateRequest request, Long userId) {
        Workflow workflow = Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType() != null ? request.getType() : "WORKFLOW")
                .replyRequirements(request.getReplyRequirements())
                .graphData(request.getGraphData())
                .status(WorkflowStatus.DRAFT)
                .userId(userId)
                .build();
        workflowRepository.save(workflow);
        return toDTO(workflow);
    }

    @Transactional
    public WorkflowDTO update(Long id, WorkflowCreateRequest request) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + id));
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        if (request.getType() != null) workflow.setType(request.getType());
        workflow.setReplyRequirements(request.getReplyRequirements());
        workflow.setGraphData(request.getGraphData());
        workflowRepository.save(workflow);
        return toDTO(workflow);
    }

    public void delete(Long id) {
        workflowRepository.deleteById(id);
    }

    public WorkflowDTO getById(Long id) {
        return toDTO(workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + id)));
    }

    public List<WorkflowDTO> listByUser(Long userId) {
        return workflowRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    private WorkflowDTO toDTO(Workflow w) {
        return WorkflowDTO.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .type(w.getType())
                .replyRequirements(w.getReplyRequirements())
                .graphData(w.getGraphData())
                .status(w.getStatus().name())
                .userId(w.getUserId())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }
}
