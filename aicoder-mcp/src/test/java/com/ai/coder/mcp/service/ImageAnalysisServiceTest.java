package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import com.ai.coder.mcp.registry.McpDynamicModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageAnalysisServiceTest {

    private ChatModel chatModel;
    private ImageAnalysisService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        McpDynamicModelRegistry registry = mock(McpDynamicModelRegistry.class);
        when(registry.getChatModel(any(String.class))).thenReturn(chatModel);
        McpProperties props = new McpProperties();
        props.getImage().setModelCode("qwen2.5-vl");
        service = new ImageAnalysisService(registry, props);
    }

    @Test
    void analyze_returns_model_text() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("图中是一只猫")))));

        String out = service.analyze("https://example.com/cat.png", "描述图片");

        assertTrue(out.contains("猫"), out);
    }

    @Test
    void analyze_handles_model_error() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("模型不可用"));

        String out = service.analyze("https://example.com/cat.png", "描述");
        assertTrue(out.contains("失败") || out.contains("不可用"), out);
    }
}
