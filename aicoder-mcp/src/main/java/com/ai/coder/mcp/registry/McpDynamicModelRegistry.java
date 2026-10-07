package com.ai.coder.mcp.registry;

import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import org.springframework.stereotype.Component;

/**
 * mcp 模块模型注册表：基类 abstract 不能直接注入，故建空子类成为 @Component。
 * image_analysis 工具注入本类，调 getChatModel(modelCode) 取多模态模型（DB 里 modelType 标 CHAT）。
 * spring-ai ChatModel 对多模态模型原生支持 image part，无需新 modelType。
 */
@Component
public class McpDynamicModelRegistry extends AbstractDynamicModelRegistry {

    public McpDynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                   ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }
}
