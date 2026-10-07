package com.ai.coder.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/** MCP 工具运行参数，经 Nacos aicoder-shared.yml 热刷新。 */
@RefreshScope
@Data
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /** 外部客户端接入 MCP server 必须携带的 access token。 */
    private String accessToken;

    /** MCP 调用归属的系统服务账号 userId（写入 X-User-Id 调下游 skill 服务）。 */
    private Long serviceUserId = 1L;

    private Search search = new Search();
    private Fetch fetch = new Fetch();
    private Image image = new Image();

    @Data
    public static class Search {
        private String provider = "tavily";
        private String apiKey;
        private int maxResults = 5;
    }

    @Data
    public static class Fetch {
        private int connectTimeout = 5000;
        private int readTimeout = 15000;
        private int maxLength = 20000;
        private boolean allowPrivateIp = false;
    }

    @Data
    public static class Image {
        /** 多模态模型 code（从 ModelConfig 取，需标 modelType=CHAT）。 */
        private String modelCode = "qwen2.5-vl";
    }
}
