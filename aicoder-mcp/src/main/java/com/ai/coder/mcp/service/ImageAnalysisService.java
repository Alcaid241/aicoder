package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import com.ai.coder.mcp.registry.McpDynamicModelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;

/**
 * 图文解析：从 McpDynamicModelRegistry 取多模态 ChatModel，构造图片 + 提问的 UserMessage 调用。
 * 支持 URL 形式图片（第一版）；base64 可后续扩展。
 *
 * <p>Spring AI 1.1.2 多模态 API：Media 位于 org.springframework.ai.content 包（spring-ai-commons），
 * UserMessage.builder().text(...).media(Media) 构造图文消息。
 * 这里用 Media.builder().data(URI) 而非 data(Resource)：后者在 build() 时立即读取字节，
 * 前者延后到模型 call() 时由模型实现拉取图片，更契合惰性加载与可测性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final McpDynamicModelRegistry registry;
    private final McpProperties props;

    public String analyze(String imageUrl, String question) {
        try {
            ChatModel chatModel = registry.getChatModel(props.getImage().getModelCode());
            UserMessage userMessage = UserMessage.builder()
                    .text(question == null ? "描述这张图片" : question)
                    .media(Media.builder()
                            .mimeType(MimeTypeUtils.IMAGE_PNG)
                            .data(new URI(imageUrl))
                            .build())
                    .build();
            ChatResponse response = chatModel.call(new Prompt(userMessage));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("image_analysis 失败 model={}", props.getImage().getModelCode(), e);
            return "图文解析失败：" + e.getMessage()
                    + "。请确认 ModelConfig 已配置多模态模型（modelType=CHAT，如 qwen2.5-vl）。";
        }
    }
}
