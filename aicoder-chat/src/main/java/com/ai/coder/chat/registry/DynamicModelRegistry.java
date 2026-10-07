package com.ai.coder.chat.registry;

import com.ai.coder.chat.dto.ModelInfoDTO;
import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chat 模块模型注册表：继承共享基类，仅补充「给前端列出可用 Chat 模型」。
 */
@Component
public class DynamicModelRegistry extends AbstractDynamicModelRegistry {

    public DynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }

    public List<ModelInfoDTO> getAvailableChatModels() {
        List<ModelConfig> models = modelConfigRepository.findByEnabledOrderBySortAsc(1);
        Map<Long, ModelProvider> providerMap = new HashMap<>();
        providerRepository.findByEnabledOrderBySortAsc(1).forEach(p -> providerMap.put(p.getId(), p));

        return models.stream()
                .filter(m -> "CHAT".equals(m.getModelType()))
                .map(m -> {
                    ModelProvider p = providerMap.get(m.getProviderId());
                    return ModelInfoDTO.builder()
                            .modelId(m.getModelCode())
                            .modelName(m.getDisplayName())
                            .provider(p != null ? p.getName() : "未知")
                            .description(p != null ? p.getDescription() : "")
                            .build();
                })
                .toList();
    }
}
