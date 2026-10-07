package com.ai.coder.core.registry;

import com.ai.coder.core.entity.ModelConfig;
import com.ai.coder.core.entity.ModelProvider;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 模型注册表基类：承载 chat/rag/workflow 三模块共享的 Chat 模型构建 + 启动探针逻辑。
 * 子类按需重写 {@link #registerExtra} 建额外模型（如 embedding）。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDynamicModelRegistry {

    protected final ModelConfigRepository modelConfigRepository;
    protected final ModelProviderRepository providerRepository;

    protected volatile Map<String, ChatModel> chatModels = new HashMap<>();

    private RestClient.Builder deepSeekRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);
        return RestClient.builder().requestFactory(factory);
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重载：重读 DB → 构建局部新表 → 原子替换 chatModels（volatile）→ 重跑探针 + registerExtra。
     * synchronized 串行化防并发 reload 互相覆盖；getChatModel 读 volatile 引用，要么旧表要么新表。
     */
    public synchronized void reload() {
        List<ModelProvider> providers = providerRepository.findByEnabledOrderBySortAsc(1);
        List<ModelConfig> models = modelConfigRepository.findByEnabledOrderBySortAsc(1);

        Map<Long, ModelProvider> providerMap = new HashMap<>();
        for (ModelProvider p : providers) {
            providerMap.put(p.getId(), p);
        }

        Map<String, ChatModel> built = new HashMap<>();
        for (ModelConfig model : models) {
            if (!"CHAT".equals(model.getModelType())) continue;
            ModelProvider provider = providerMap.get(model.getProviderId());
            if (provider == null) continue;

            ChatModel chatModel = createChatModel(provider, model.getModelCode());
            if (chatModel != null) {
                built.put(model.getModelCode(), chatModel);
                log.info("注册 Chat 模型: {} ({})", model.getDisplayName(), model.getModelCode());
                if ("OLLAMA".equals(provider.getCode())) {
                    probeOllama(provider.getBaseUrl(), model.getDisplayName(), model.getModelCode());
                }
            }
        }
        // 钩子：子类可在此建额外模型（如 embedding）
        registerExtra(models, providerMap);
        // 原子替换：并发 getChatModel 读到旧表或新表，不会半破
        this.chatModels = built;
        log.info("DynamicModelRegistry 初始化完成，共 {} 个 Chat 模型", chatModels.size());
    }

    protected ChatModel createChatModel(ModelProvider provider, String modelCode) {
        return switch (provider.getCode()) {
            case "OLLAMA" -> OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(provider.getBaseUrl()).build())
                    // Spring AI 1.1.2 默认开启 thinking，非推理模型（gemma3/deepseek-coder 等）会被
                    // Ollama 拒绝：400 "xxx does not support thinking"。显式关闭，保证通用模型可用。
                    .defaultOptions(OllamaChatOptions.builder().model(modelCode).disableThinking().build())
                    .build();
            case "DEEPSEEK" -> DeepSeekChatModel.builder()
                    .deepSeekApi(DeepSeekApi.builder()
                            .apiKey(provider.getApiKey())
                            .restClientBuilder(deepSeekRestClientBuilder())
                            .build())
                    .defaultOptions(DeepSeekChatOptions.builder()
                            .model(modelCode)
                            .maxTokens(8192)
                            .build())
                    .build();
            default -> {
                log.warn("不支持的厂商类型: {}", provider.getCode());
                yield null;
            }
        };
    }

    /**
     * 启动探针：校验 Ollama 可达 + tag 已拉取（仅查 {@code /api/tags}，不加载模型）。失败只 WARN 不阻断。
     */
    @SuppressWarnings("unchecked")
    protected void probeOllama(String baseUrl, String displayName, String modelCode) {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3_000);
            factory.setReadTimeout(5_000);
            Map<String, Object> resp = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(Map.class);
            List<String> pulled = new ArrayList<>();
            if (resp != null && resp.get("models") instanceof List<?> list) {
                for (Object m : list) {
                    if (m instanceof Map<?, ?> mm && mm.get("name") instanceof String n) {
                        pulled.add(n);
                    }
                }
            }
            boolean tagPulled = pulled.stream()
                    .anyMatch(n -> n.equals(modelCode) || n.startsWith(modelCode + ":"));
            if (!tagPulled) {
                log.warn("⚠️ [模型探针] {} ({}) 的 tag 未在 Ollama 拉取列表中。已拉取：{}。请执行 `ollama pull {}`",
                        displayName, modelCode, pulled, modelCode);
            }
        } catch (Exception e) {
            log.warn("⚠️ [模型探针] {} ({}) 不可达：Ollama({}) 连接失败——{}。请确认 Ollama 已运行（`ollama serve`）",
                    displayName, modelCode, baseUrl, e.getMessage());
        }
    }

    /** 子类钩子：CHAT 模型注册后、收尾日志前，建额外模型（如 embedding）。默认空。 */
    protected void registerExtra(List<ModelConfig> models, Map<Long, ModelProvider> providerMap) {
    }

    public ChatModel getChatModel(String modelCode) {
        ChatModel model = chatModels.get(modelCode);
        if (model == null) {
            throw new IllegalArgumentException("不支持的模型: " + modelCode);
        }
        return model;
    }

    public Map<String, ChatModel> getChatModelMap() {
        return Collections.unmodifiableMap(chatModels);
    }
}
