package com.ai.coder.admin.init;

import com.ai.coder.admin.entity.ModelConfig;
import com.ai.coder.admin.entity.ModelProvider;
import com.ai.coder.admin.repository.ModelConfigRepository;
import com.ai.coder.admin.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class ModelDataInitializer implements CommandLineRunner {

    private final ModelProviderRepository providerRepository;
    private final ModelConfigRepository modelConfigRepository;

    @Override
    public void run(String... args) {
        initProviders();
        initModels();
    }

    private void initProviders() {
        if (providerRepository.findByCode("OLLAMA").isPresent()) {
            return;
        }
        saveProvider("Ollama", null, "OLLAMA",
                "http://localhost:11434", null,
                "本地 Ollama 推理服务", 0);
        saveProvider("DeepSeek", null, "DEEPSEEK",
                "https://api.deepseek.com",
                System.getenv().getOrDefault("DEEPSEEK_API_KEY", ""),
                "DeepSeek 云端 API 服务", 1);
        log.info("初始化厂商数据");
    }

    private void initModels() {
        if (modelConfigRepository.count() > 0) {
            return;
        }
        ModelProvider ollama = providerRepository.findByCode("OLLAMA").orElseThrow();
        ModelProvider deepseek = providerRepository.findByCode("DEEPSEEK").orElseThrow();

        saveModel(ollama.getId(), "Gemma 3 4B", "gemma3:4b", "CHAT", 0, 0);
        saveModel(ollama.getId(), "DeepSeek Coder", "deepseek-coder", "CHAT", 1, 0);
        saveModel(deepseek.getId(), "DeepSeek V4 Flash", "deepseek-v4-flash", "CHAT", 2, 1);
        saveModel(ollama.getId(), "Nomic Embed Text", "nomic-embed-text", "EMBEDDING", 0, 0);
        log.info("初始化模型配置数据");
    }

    private void saveProvider(String name, String logo, String code,
                              String baseUrl, String apiKey, String description, int sort) {
        ModelProvider p = new ModelProvider();
        p.setName(name);
        p.setLogo(logo);
        p.setCode(code);
        p.setBaseUrl(baseUrl);
        p.setApiKey(apiKey);
        p.setDescription(description);
        p.setEnabled(1);
        p.setSort(sort);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        providerRepository.save(p);
    }

    private void saveModel(Long providerId, String displayName,
                           String modelCode, String modelType, int sort, int supportTools) {
        ModelConfig m = new ModelConfig();
        m.setProviderId(providerId);
        m.setDisplayName(displayName);
        m.setModelCode(modelCode);
        m.setModelType(modelType);
        m.setEnabled(1);
        m.setSort(sort);
        m.setSupportTools(supportTools);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        modelConfigRepository.save(m);
    }
}
