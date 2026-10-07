package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.service.ImageAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** image_analysis 工具：用多模态 LLM 解析图片内容。 */
@Component
@RequiredArgsConstructor
public class ImageAnalysisTool {

    private final ImageAnalysisService imageAnalysisService;

    @Tool(description = "图文解析：用多模态模型解析图片内容并回答问题。参数：image（图片 URL）、question（想了解什么）。")
    public String imageAnalysis(String image, String question) {
        if (image == null || image.isBlank()) {
            return "图片地址不能为空。";
        }
        return imageAnalysisService.analyze(image.trim(), question);
    }
}
