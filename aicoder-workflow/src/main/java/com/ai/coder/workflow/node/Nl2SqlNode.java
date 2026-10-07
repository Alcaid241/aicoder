package com.ai.coder.workflow.node;

import com.ai.coder.workflow.service.WorkflowSqlService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Nl2SqlNode implements NodeAction {

    private final WorkflowSqlService sqlService;
    private final String knowledgeBaseId;
    private final String model;
    private final String databaseName;
    private final String inputKey;
    private final String outputKey;

    public Nl2SqlNode(WorkflowSqlService sqlService, String knowledgeBaseId, String model,
                      String databaseName, String inputKey, String outputKey) {
        this.sqlService = sqlService;
        this.knowledgeBaseId = knowledgeBaseId;
        this.model = model;
        this.databaseName = databaseName;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(inputKey, "").toString();
        log.info("Nl2SqlNode 执行: question={}, kbId={}, model={}", question, knowledgeBaseId, model);

        String result = sqlService.generateAndExecute(question, knowledgeBaseId, model, databaseName);

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, result);
        return output;
    }
}
