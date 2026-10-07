package com.ai.coder.chat.controller;

import com.ai.coder.chat.dto.ModelInfoDTO;
import com.ai.coder.chat.registry.DynamicModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ModelController {

    private final DynamicModelRegistry modelRegistry;

    @GetMapping("/models")
    public List<ModelInfoDTO> models() {
        return modelRegistry.getAvailableChatModels();
    }
}
