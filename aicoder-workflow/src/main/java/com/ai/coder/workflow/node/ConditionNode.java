package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ConditionNode implements NodeAction {

    private final String inputKey;
    private final List<Map<String, String>> conditions;

    public ConditionNode(String inputKey, List<Map<String, String>> conditions) {
        this.inputKey = inputKey;
        this.conditions = conditions;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object inputValue = state.value(inputKey, "");
        String value = inputValue != null ? inputValue.toString() : "";

        String matchedRoute = "__default__";
        for (Map<String, String> cond : conditions) {
            String match = cond.get("match");
            if (!"__default__".equals(match) && value.contains(match)) {
                matchedRoute = cond.get("label");
                break;
            }
        }

        if ("__default__".equals(matchedRoute)) {
            for (Map<String, String> cond : conditions) {
                if ("__default__".equals(cond.get("match"))) {
                    matchedRoute = cond.get("label");
                    break;
                }
            }
        }

        log.info("ConditionNode 执行: inputKey={}, value='{}', route={}", inputKey, value, matchedRoute);

        Map<String, Object> output = new HashMap<>();
        output.put("__route__", matchedRoute);
        return output;
    }
}
