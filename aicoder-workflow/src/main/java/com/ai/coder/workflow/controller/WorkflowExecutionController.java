package com.ai.coder.workflow.controller;

import com.ai.coder.workflow.model.dto.NodeExecutionDTO;
import com.ai.coder.workflow.model.dto.WorkflowExecutionRequest;
import com.ai.coder.workflow.model.entity.WorkflowExecution;
import com.ai.coder.workflow.model.entity.WorkflowNodeExecution;
import com.ai.coder.workflow.repository.WorkflowExecutionRepository;
import com.ai.coder.workflow.repository.WorkflowNodeExecutionRepository;
import com.ai.coder.workflow.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowExecutionController {

    private final WorkflowExecutionService executionService;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowNodeExecutionRepository nodeExecutionRepository;

    @PostMapping(value = "/{id}/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> execute(@PathVariable Long id,
                                                  @RequestBody WorkflowExecutionRequest request,
                                                  @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) throws Exception {
        return executionService.executeWorkflow(id, request, userId);
    }

    @GetMapping("/executions/{executionId}")
    public WorkflowExecution getExecution(@PathVariable Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("执行记录不存在: " + executionId));
    }

    @GetMapping("/executions/{executionId}/nodes")
    public List<NodeExecutionDTO> getNodeExecutions(@PathVariable Long executionId) {
        return nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId).stream()
                .map(this::toNodeDTO)
                .toList();
    }

    @GetMapping("/{id}/executions")
    public List<WorkflowExecution> getWorkflowExecutions(@PathVariable Long id) {
        return executionRepository.findByWorkflowIdOrderByStartedAtDesc(id);
    }

    private NodeExecutionDTO toNodeDTO(WorkflowNodeExecution e) {
        return NodeExecutionDTO.builder()
                .id(e.getId())
                .executionId(e.getExecutionId())
                .nodeId(e.getNodeId())
                .nodeName(e.getNodeName())
                .nodeType(e.getNodeType())
                .status(e.getStatus().name())
                .inputData(e.getInputData())
                .outputData(e.getOutputData())
                .errorMessage(e.getErrorMessage())
                .startedAt(e.getStartedAt())
                .finishedAt(e.getFinishedAt())
                .build();
    }
}
