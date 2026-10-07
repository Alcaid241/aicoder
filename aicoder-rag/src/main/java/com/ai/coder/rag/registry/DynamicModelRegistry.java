package com.ai.coder.rag.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * rag 模块模型注册表：继承共享基类，重写 registerExtra 建 embedding（首个 EMBEDDING 模型）。
 */
@Slf4j
@Component
public class DynamicModelRegistry extends AbstractDynamicModelRegistry {

    private EmbeddingModel embeddingModel;

    public DynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }

    @Override
    protected void registerExtra(List<ModelConfig> models, Map<Long, ModelProvider> providerMap) {
        if (embeddingModel != null) return;
        for (ModelConfig model : models) {
            if (!"EMBEDDING".equals(model.getModelType())) continue;
            ModelProvider provider = providerMap.get(model.getProviderId());
            if (provider == null || !"OLLAMA".equals(provider.getCode())) continue;
            embeddingModel = OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(provider.getBaseUrl()).build())
                    .defaultOptions(OllamaEmbeddingOptions.builder().model(model.getModelCode()).build())
                    .build();
            log.info("注册 Embedding 模型: {} ({})", model.getDisplayName(), model.getModelCode());
            probeOllama(provider.getBaseUrl(), model.getDisplayName(), model.getModelCode());
            return; // 首个 EMBEDDING 模型即用（沿用现状）
        }
    }

    public EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            throw new IllegalStateException("没有可用的 Embedding 模型，请在管理页面配置");
        }
        return embeddingModel;
    }
}
