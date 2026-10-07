package com.ai.coder.core.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractDynamicModelRegistryTest {

    @Test
    void init_registers_chat_models_and_resolves_them() {
        // 用 DEEPSEEK（非 OLLAMA）→ 探针跳过，测试不依赖 Ollama 运行，保持 hermetic
        ModelProvider deepseek = new ModelProvider();
        deepseek.setId(1L);
        deepseek.setCode("DEEPSEEK");
        deepseek.setApiKey("sk-test");
        deepseek.setBaseUrl("https://api.deepseek.com");

        ModelConfig cfg = new ModelConfig();
        cfg.setProviderId(1L);
        cfg.setModelCode("deepseek-test");
        cfg.setDisplayName("Test");
        cfg.setModelType("CHAT");

        ModelConfigRepository modelConfigRepo = mock(ModelConfigRepository.class);
        ModelProviderRepository providerRepo = mock(ModelProviderRepository.class);
        when(providerRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(deepseek));
        when(modelConfigRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(cfg));

        AbstractDynamicModelRegistry registry = new AbstractDynamicModelRegistry(modelConfigRepo, providerRepo) {
        };
        registry.init(); // @PostConstruct 在纯单测不自动触发，手动调用

        ChatModel resolved = registry.getChatModel("deepseek-test");
        assertNotNull(resolved, "init 应注册 deepseek-test");
        assertThrows(IllegalArgumentException.class, () -> registry.getChatModel("nope"),
                "未注册的模型应抛 IllegalArgumentException");
    }

    @Test
    void reload_rebuilds_registry_with_new_model_set() {
        ModelProvider deepseek = new ModelProvider();
        deepseek.setId(1L);
        deepseek.setCode("DEEPSEEK");
        deepseek.setApiKey("sk-test");
        deepseek.setBaseUrl("https://api.deepseek.com");

        ModelConfig first = new ModelConfig();
        first.setProviderId(1L); first.setModelCode("deepseek-a"); first.setDisplayName("A"); first.setModelType("CHAT");
        ModelConfig second = new ModelConfig();
        second.setProviderId(1L); second.setModelCode("deepseek-b"); second.setDisplayName("B"); second.setModelType("CHAT");

        ModelConfigRepository modelConfigRepo = mock(ModelConfigRepository.class);
        ModelProviderRepository providerRepo = mock(ModelProviderRepository.class);
        when(providerRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(deepseek));
        when(modelConfigRepo.findByEnabledOrderBySortAsc(1)).thenReturn(List.of(first))
                .thenReturn(List.of(second)); // 第一次 init 用 first，第二次 reload 用 second

        AbstractDynamicModelRegistry registry = new AbstractDynamicModelRegistry(modelConfigRepo, providerRepo) {};
        registry.init();
        assertNotNull(registry.getChatModel("deepseek-a"), "init 注册 deepseek-a");

        registry.reload();
        assertNotNull(registry.getChatModel("deepseek-b"), "reload 后新模型 deepseek-b 可用");
        assertThrows(IllegalArgumentException.class, () -> registry.getChatModel("deepseek-a"),
                "reload 后旧模型 deepseek-a 应已被新表替换掉");
    }
}
