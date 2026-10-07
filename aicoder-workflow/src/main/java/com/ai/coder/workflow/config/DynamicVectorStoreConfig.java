package com.ai.coder.workflow.config;

import com.ai.coder.workflow.model.entity.VectorDbConfig;
import com.ai.coder.workflow.registry.DynamicModelRegistry;
import com.ai.coder.workflow.repository.VectorDbConfigRepository;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DynamicVectorStoreConfig {

    private final VectorDbConfigRepository vectorDbConfigRepository;
    private final DynamicModelRegistry modelRegistry;

    private String resolveCollectionName(VectorDbConfig config) {
        return (config.getDatabaseName() != null && !config.getDatabaseName().isBlank())
                ? config.getDatabaseName() : "ai_vectors";
    }

    public VectorStore getVectorStore() {
        VectorDbConfig config = vectorDbConfigRepository.findByActiveTrue()
                .orElseThrow(() -> new IllegalStateException("没有激活的向量数据库配置，请先在管理页面配置并激活一个向量数据库"));

        log.info("使用向量数据库: type={}, host={}, port={}, collection={}",
                config.getDbType(), config.getHost(), config.getPort(), resolveCollectionName(config));

        return switch (config.getDbType()) {
            case "REDIS" -> buildRedisVectorStore(config);
            case "CHROMA" -> buildChromaVectorStore(config);
            case "MILVUS" -> buildMilvusVectorStore(config);
            default -> throw new IllegalArgumentException("不支持的向量数据库类型: " + config.getDbType());
        };
    }

    public boolean hasActiveConfig() {
        return vectorDbConfigRepository.findByActiveTrue().isPresent();
    }

    private VectorStore buildRedisVectorStore(VectorDbConfig config) {
        JedisPooled jedis;
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            jedis = new JedisPooled(config.getHost(), config.getPort(), "default", config.getPassword());
        } else {
            jedis = new JedisPooled(config.getHost(), config.getPort());
        }

        RedisVectorStore store = RedisVectorStore.builder(jedis, modelRegistry.getEmbeddingModel())
                .indexName(resolveCollectionName(config))
                .prefix("ai_doc:")
                .initializeSchema(true)
                .build();

        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("初始化 Redis VectorStore 失败", e);
        }

        return store;
    }

    private VectorStore buildChromaVectorStore(VectorDbConfig config) {
        String baseUrl = "http://" + config.getHost() + ":" + config.getPort();
        String collectionName = resolveCollectionName(config);

        ChromaApi chromaApi = ChromaApi.builder()
                .baseUrl(baseUrl)
                .build();

        // ChromaApi.getCollection/getTenant/getDatabase 在 Spring AI 1.1.2 中有错误消息匹配 bug：
        // 期望 ChromaDB 返回 "Collection [X] does not exists" 等格式，但 ChromaDB 0.5.17 实际返回
        // "Collection X does not exist."，导致匹配失败抛出异常而非返回 null。
        // 因此提前创建 tenant、database、collection，让 afterPropertiesSet() 能直接找到它们。
        try {
            chromaApi.createTenant("SpringAiTenant");
        } catch (Exception ignored) { /* 可能已存在 */ }
        try {
            chromaApi.createDatabase("SpringAiTenant", "SpringAiDatabase");
        } catch (Exception ignored) { /* 可能已存在 */ }
        try {
            chromaApi.createCollection("SpringAiTenant", "SpringAiDatabase",
                    new ChromaApi.CreateCollectionRequest(collectionName));
        } catch (Exception ignored) { /* 可能已存在 */ }

        ChromaVectorStore store = ChromaVectorStore.builder(chromaApi, modelRegistry.getEmbeddingModel())
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();

        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("初始化 Chroma VectorStore 失败: " + e.getMessage(), e);
        }

        return store;
    }

    private VectorStore buildMilvusVectorStore(VectorDbConfig config) {
        String host = config.getHost();
        int port = config.getPort() != null ? config.getPort() : 19530;

        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port);

        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            connectBuilder.withAuthorization(config.getUsername(), config.getPassword() != null ? config.getPassword() : "");
        }

        MilvusServiceClient milvusClient = new MilvusServiceClient(connectBuilder.build());

        MilvusVectorStore store = MilvusVectorStore.builder(milvusClient, modelRegistry.getEmbeddingModel())
                .collectionName(resolveCollectionName(config))
                .databaseName("default")
                .initializeSchema(true)
                .build();

        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("初始化 Milvus VectorStore 失败: " + e.getMessage(), e);
        }

        return store;
    }
}
