package com.ai.coder.mcp.config;

import com.ai.coder.mcp.tool.CalculatorTool;
import com.ai.coder.mcp.tool.ImageAnalysisTool;
import com.ai.coder.mcp.tool.SkillTool;
import com.ai.coder.mcp.tool.WebFetchTool;
import com.ai.coder.mcp.tool.WebSearchTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP server 工具装配：把带 @Tool 注解的工具 bean 聚合为 {@link ToolCallbackProvider}，
 * spring-ai-starter-mcp-server-webmvc 自动捕获并经 streamable-http 暴露为 MCP tools。
 * 每新增工具在此追加参数。
 *
 * <p>当前最终形态（5 工具）：calculator、skill、web_search、web_fetch、image_analysis。</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbacks(CalculatorTool calculatorTool,
                                                 SkillTool skillTool,
                                                 WebSearchTool webSearchTool,
                                                 WebFetchTool webFetchTool,
                                                 ImageAnalysisTool imageAnalysisTool) {
        // ToolCallbacks.from 把带 @Tool 的 bean 转为 ToolCallback[]，
        // 再由 ToolCallbackProvider.from 聚合为 provider（被 MCP server 自动捕获）
        return ToolCallbackProvider.from(ToolCallbacks.from(
                calculatorTool, skillTool, webSearchTool, webFetchTool, imageAnalysisTool));
    }
}
