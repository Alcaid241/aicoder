package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.service.WebFetchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** web_fetch 工具：抓取指定网页正文并转为易读 markdown 文档。 */
@Component
@RequiredArgsConstructor
public class WebFetchTool {

    private final WebFetchService webFetchService;

    @Tool(description = "抓取指定网页内容并转换为易读的 markdown 文档格式。参数 url 为目标网页地址。")
    public String webFetch(String url) {
        if (url == null || url.isBlank()) {
            return "URL 不能为空。";
        }
        return webFetchService.fetch(url.trim());
    }
}
