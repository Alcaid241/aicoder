package com.ai.coder.chat.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
public class HttpRequestTool {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String getName() {
        return "http_request";
    }

    public String getDescription() {
        return "发起 HTTP 请求获取外部 API 数据。当需要调用外部接口或获取网页内容时使用此工具。";
    }

    public String execute(String url, String method) {
        log.info("HttpRequestTool 执行: url={}, method={}", url, method);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(""));
            } else {
                requestBuilder.GET();
            }

            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body.length() > 2000) {
                body = body.substring(0, 2000) + "...(截断)";
            }
            return "HTTP " + response.statusCode() + ": " + body;
        } catch (Exception e) {
            log.warn("HTTP 请求失败: {}", e.getMessage());
            return "HTTP 请求失败: " + e.getMessage();
        }
    }
}
