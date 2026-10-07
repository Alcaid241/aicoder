package com.ai.coder.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ToolNode implements NodeAction {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final String bodyTemplate;
    private final String inputKey;
    private final String outputKey;

    public ToolNode(String url, String method, Map<String, String> headers,
                    String bodyTemplate, String inputKey, String outputKey) {
        this.url = url;
        this.method = method != null ? method.toUpperCase() : "GET";
        this.headers = headers != null ? headers : Map.of();
        this.bodyTemplate = bodyTemplate;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(inputKey, "").toString();
        String body = bodyTemplate != null ? bodyTemplate.replace("{{input}}", input) : input;

        log.info("ToolNode 执行: method={}, url={}, inputLength={}", method, url, input.length());

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        for (Map.Entry<String, String> h : headers.entrySet()) {
            requestBuilder.header(h.getKey(), h.getValue());
        }

        if ("GET".equals(method)) {
            requestBuilder.GET();
        } else if ("POST".equals(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
            if (!headers.containsKey("Content-Type")) {
                requestBuilder.header("Content-Type", "application/json");
            }
        } else if ("PUT".equals(method)) {
            requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
        } else if ("DELETE".equals(method)) {
            requestBuilder.DELETE();
        }

        HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        log.info("ToolNode 响应: status={}", response.statusCode());

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, response.body());
        return output;
    }
}
