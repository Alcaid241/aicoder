package com.ai.coder.workflow.node;

import com.ai.coder.workflow.model.entity.Workflow;
import com.ai.coder.workflow.repository.WorkflowRepository;
import com.ai.coder.workflow.service.WorkflowExecutionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SubWorkflowNode implements NodeAction {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionService executionService;
    private final Long targetWorkflowId;
    private final String inputKey;
    private final String outputKey;

    public SubWorkflowNode(WorkflowRepository workflowRepository,
                           WorkflowExecutionService executionService,
                           Long targetWorkflowId, String inputKey, String outputKey) {
        this.workflowRepository = workflowRepository;
        this.executionService = executionService;
        this.targetWorkflowId = targetWorkflowId;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(inputKey, "").toString();
        log.info("SubWorkflowNode 执行: workflowId={}, inputLength={}", targetWorkflowId, input.length());

        Workflow subWorkflow = workflowRepository.findById(targetWorkflowId)
                .orElseThrow(() -> new IllegalArgumentException("子工作流不存在: " + targetWorkflowId));

        Map<String, Object> subInput = new HashMap<>();
        subInput.put("query", input);

        String result = executionService.executeSubWorkflow(subWorkflow, subInput);

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, result);
        return output;
    }
}
