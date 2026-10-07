package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LlmNode implements NodeAction {

    private final ChatModel chatModel;
    private final String promptTemplate;
    private final String inputKey;
    private final String outputKey;

    public LlmNode(ChatModel chatModel, String promptTemplate, String inputKey, String outputKey) {
        this.chatModel = chatModel;
        this.promptTemplate = promptTemplate;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(inputKey, "").toString();
        String prompt = promptTemplate.replace("{{input}}", input);

        log.info("LlmNode 执行: inputKey={}, outputKey={}, input长度={}", inputKey, outputKey, input.length());

        String result = chatModel.call(new Prompt(List.of(new UserMessage(prompt))))
                .getResult().getOutput().getText();

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, result);
        return output;
    }
}
