package com.ai.coder.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * MCP 工具的 REST 包装端点——供内部服务（如 aicoder-chat）动态获取工具列表和调用工具。
 * 不需要 MCP 协议握手（SSE/JSON-RPC），直接用 HTTP GET/POST。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpToolController {

    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper;

    public McpToolController(ToolCallbackProvider toolCallbackProvider, ObjectMapper objectMapper) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.objectMapper = objectMapper;
    }

    /** 获取当前注册的所有 MCP 工具列表 */
    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> listTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolCallback tc : callbacks) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", tc.getToolDefinition().name());
            tool.put("description", tc.getToolDefinition().description());
            try {
                tool.put("inputSchema", objectMapper.readTree(tc.getToolDefinition().inputSchema()));
            } catch (Exception e) {
                tool.put("inputSchema", Collections.emptyMap());
            }
            tools.add(tool);
        }
        return ResponseEntity.ok(tools);
    }

    /** 调用指定工具。arguments 为 JSON 对象，内部转为 JSON 字符串传给 ToolCallback.call(String) */
    @PostMapping("/call/{toolName}")
    public ResponseEntity<Map<String, Object>> callTool(@PathVariable String toolName,
                                                        @RequestBody Map<String, Object> arguments) {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        for (ToolCallback tc : callbacks) {
            if (tc.getToolDefinition().name().equals(toolName)) {
                try {
                    String argsJson = objectMapper.writeValueAsString(arguments);
                    String result = tc.call(argsJson);
                    return ResponseEntity.ok(Map.of("success", true, "result", result));
                } catch (Exception e) {
                    return ResponseEntity.status(500).body(Map.of(
                            "success", false,
                            "error", e.getMessage() != null ? e.getMessage() : "工具调用失败"));
                }
            }
        }
        return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "未知工具: " + toolName));
    }
}
