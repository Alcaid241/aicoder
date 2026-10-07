package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RagNode implements NodeAction {

    private final VectorStore vectorStore;
    private final String knowledgeBaseId;
    private final int topK;
    private final String inputKey;
    private final String outputKey;

    public RagNode(VectorStore vectorStore, String knowledgeBaseId, int topK, String inputKey, String outputKey) {
        this.vectorStore = vectorStore;
        this.knowledgeBaseId = knowledgeBaseId;
        this.topK = topK;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String query = state.value(inputKey, "").toString();

        log.info("RagNode 执行: query={}, knowledgeBaseId={}, topK={}", query, knowledgeBaseId, topK);

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression("knowledgeBaseId == '" + knowledgeBaseId + "'")
                        .build()
        );

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, context);
        return output;
    }
}
