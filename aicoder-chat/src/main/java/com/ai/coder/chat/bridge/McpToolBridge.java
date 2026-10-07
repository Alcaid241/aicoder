package com.ai.coder.chat.bridge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具桥接——通过 REST 调 aicoder-mcp 的工具列表和调用端点。
 * 使用 @LoadBalanced RestTemplate（通过 Nacos 解析 aicoder-mcp 服务名）。
 * mcp 的 /api/mcp/tools 和 /api/mcp/call 在 McpAuthFilter 中放行，无需 token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolBridge {

    private static final String MCP_BASE = "http://aicoder-mcp/api/mcp";

    private final RestTemplate restTemplate;

    /** 获取 mcp 服务的工具列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTools() {
        try {
            List<Map<String, Object>> tools = restTemplate.getForObject(
                    MCP_BASE + "/tools", List.class);
            return tools != null ? tools : Collections.emptyList();
        } catch (Exception e) {
            log.warn("获取 mcp 工具列表失败 (mcp 服务可能未启动): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 调用 mcp 服务的指定工具 */
    @SuppressWarnings("unchecked")
    public String callTool(String toolName, Map<String, Object> args) {
        try {
            Map<String, Object> resp = restTemplate.postForObject(
                    MCP_BASE + "/call/" + toolName, args, Map.class);
            if (resp == null) return "工具返回空";
            boolean success = Boolean.TRUE.equals(resp.get("success"));
            if (success) {
                return String.valueOf(resp.getOrDefault("result", ""));
            }
            return "工具调用失败: " + resp.getOrDefault("error", "未知错误");
        } catch (Exception e) {
            log.warn("调用 mcp 工具 {} 失败: {}", toolName, e.getMessage());
            return "调用 mcp 工具失败: " + e.getMessage();
        }
    }
}
