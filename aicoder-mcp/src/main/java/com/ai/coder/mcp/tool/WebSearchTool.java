package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** web_search 工具：网络搜索，返回标题+摘要+链接。 */
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private final WebSearchService webSearchService;

    @Tool(description = "网络搜索：根据查询词搜索网络信息，返回若干条结果（标题、链接、摘要）。")
    public String webSearch(String query) {
        if (query == null || query.isBlank()) {
            return "查询词不能为空。";
        }
        return webSearchService.search(query.trim());
    }
}
