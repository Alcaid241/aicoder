package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LoopNode implements NodeAction {

    private final int maxIterations;
    private final String exitCondition;
    private final String inputKey;
    private final String outputKey;

    public LoopNode(int maxIterations, String exitCondition, String inputKey, String outputKey) {
        this.maxIterations = maxIterations;
        this.exitCondition = exitCondition;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String value = state.value(inputKey, "").toString();
        int iteration = 0;
        Object iterObj = state.value("__loop_iteration__", null);
        if (iterObj != null) {
            iteration = Integer.parseInt(iterObj.toString());
        }
        iteration++;

        boolean shouldExit = false;
        if (exitCondition != null && !exitCondition.isEmpty() && value.contains(exitCondition)) {
            shouldExit = true;
        }
        if (iteration >= maxIterations) {
            shouldExit = true;
        }

        log.info("LoopNode 迭代 {}/{}, shouldExit={}, valueLength={}",
                iteration, maxIterations, shouldExit, value.length());

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, value);
        output.put("__loop_iteration__", iteration);

        return output;
    }
}
