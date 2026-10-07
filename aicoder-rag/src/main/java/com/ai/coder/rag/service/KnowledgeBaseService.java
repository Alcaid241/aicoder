package com.ai.coder.rag.service;

import com.ai.coder.rag.config.DynamicVectorStoreConfig;
import com.ai.coder.rag.dto.KnowledgeBaseCreateRequest;
import com.ai.coder.rag.dto.KnowledgeBaseDTO;
import com.ai.coder.rag.dto.KnowledgeBaseUpdateRequest;
import com.ai.coder.rag.entity.KnowledgeBase;
import com.ai.coder.rag.entity.KnowledgeDocument;
import com.ai.coder.rag.entity.VectorDbConfig;
import com.ai.coder.rag.repository.KnowledgeBaseRepository;
import com.ai.coder.rag.repository.KnowledgeDocumentRepository;
import com.ai.coder.rag.repository.VectorDbConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final VectorDbConfigRepository vectorDbConfigRepository;
    private final DocumentParserService documentParserService;
    private final DynamicVectorStoreConfig dynamicVectorStoreConfig;

    @Transactional
    public KnowledgeBaseDTO createKnowledgeBase(KnowledgeBaseCreateRequest request, Long userId) {
        if (request.getVectorDbConfigId() == null) {
            throw new IllegalArgumentException("请选择向量数据库配置");
        }

        VectorDbConfig vectorDbConfig = vectorDbConfigRepository.findById(request.getVectorDbConfigId())
                .orElseThrow(() -> new IllegalArgumentException("向量数据库配置不存在: " + request.getVectorDbConfigId()));

        if (!Boolean.TRUE.equals(vectorDbConfig.getActive())) {
            throw new IllegalStateException("所选向量数据库配置未激活，请先在「向量库配置」页面激活该配置");
        }

        KnowledgeBase kb = KnowledgeBase.builder()
                .userId(userId)
                .name(request.getName())
                .type(request.getType())
                .systemPrompt(request.getSystemPrompt())
                .databaseName(request.getDatabaseName())
                .jdbcUrl(request.getJdbcUrl())
                .dbUsername(request.getDbUsername())
                .dbPassword(request.getDbPassword())
                .description(request.getDescription())
                .vectorDbType(vectorDbConfig.getDbType())
                .vectorDbConfigId(vectorDbConfig.getId())
                .vectorCollection("kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .documentCount(0)
                .status(1)
                .build();

        kb = knowledgeBaseRepository.save(kb);
        return toDTO(kb);
    }

    public List<KnowledgeDocument> getDocuments(Long knowledgeBaseId) {
        return knowledgeDocumentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Transactional
    public KnowledgeBaseDTO updateKnowledgeBase(Long id, KnowledgeBaseUpdateRequest request, Long userId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + id));

        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权修改该知识库");
        }

        kb.setName(request.getName());
        kb.setType(request.getType());
        kb.setSystemPrompt(request.getSystemPrompt());
        kb.setDatabaseName(request.getDatabaseName());
        kb.setJdbcUrl(request.getJdbcUrl());
        kb.setDbUsername(request.getDbUsername());
        kb.setDbPassword(request.getDbPassword());
        kb.setDescription(request.getDescription());
        if (request.getVectorDbConfigId() != null) {
            kb.setVectorDbConfigId(request.getVectorDbConfigId());
        }

        kb = knowledgeBaseRepository.save(kb);
        return toDTO(kb);
    }

    public List<KnowledgeBaseDTO> listKnowledgeBases(Long userId) {
        return knowledgeBaseRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    public KnowledgeDocument uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        logMemory("上传开始");

        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + knowledgeBaseId));

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .fileName(file.getOriginalFilename())
                .fileType(getFileExtension(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .status("PROCESSING")
                .build();
        doc = knowledgeDocumentRepository.save(doc);

        // 保存原始文件到磁盘
        try {
            Path uploadDir = Paths.get("uploads/knowledge", String.valueOf(knowledgeBaseId));
            Files.createDirectories(uploadDir);
            String savedName = doc.getId() + "_" + file.getOriginalFilename();
            Path filePath = uploadDir.resolve(savedName);
            file.transferTo(filePath.toFile());
            doc.setFilePath(filePath.toString());
            doc = knowledgeDocumentRepository.save(doc);
        } catch (Exception e) {
            log.warn("保存原始文件失败: {}", e.getMessage());
        }

        logMemory("保存文档记录后");

        String text;
        try {
            text = documentParserService.parseDocument(file, doc.getFileType());
            log.info("文档解析完成，文本长度: {}", text.length());
            logMemory("Tika解析后");
        } catch (Exception e) {
            log.error("文档解析失败: {}", e.getMessage(), e);
            updateDocError(doc.getId(), "文档解析失败: " + e.getMessage());
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }

        List<String> chunks = documentParserService.splitToChunks(text, 800, 200);
        text = null;
        log.info("文档分块完成，块数: {}", chunks.size());
        logMemory("分块完成后");

        try {
            log.info("开始获取VectorStore...");
            VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore(kb.getVectorDbType());

            int batchSize = 10;
            for (int batch = 0; batch < chunks.size(); batch += batchSize) {
                int end = Math.min(batch + batchSize, chunks.size());
                List<Document> batchDocs = new ArrayList<>();
                for (int i = batch; i < end; i++) {
                    batchDocs.add(new Document(
                            UUID.randomUUID().toString(),
                            chunks.get(i),
                            Map.of(
                                    "knowledgeBaseId", String.valueOf(knowledgeBaseId),
                                    "documentId", String.valueOf(doc.getId()),
                                    "fileName", doc.getFileName(),
                                    "chunkIndex", String.valueOf(i)
                            )
                    ));
                }
                log.info("开始向量化第 {}-{}/{} 块", batch + 1, end, chunks.size());
                vectorStore.add(batchDocs);
                batchDocs.clear();
                logMemory("向量化 " + end + "/" + chunks.size() + " 后");
            }

            updateDocSuccess(doc.getId(), chunks.size());
            incrementKbDocCount(kb.getId());
            log.info("上传处理全部完成");
        } catch (Exception e) {
            log.error("向量化失败: {}", e.getMessage(), e);
            updateDocError(doc.getId(), "向量化失败: " + e.getMessage());
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }

        return knowledgeDocumentRepository.findById(doc.getId()).orElse(doc);
    }

    private void logMemory(String step) {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory() / 1024 / 1024;
        long total = rt.totalMemory() / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        long used = total - free;
        log.info("[内存] {} — 已用:{}MB, 总共:{}MB, 最大:{}MB", step, used, total, max);
    }

    @Transactional
    public void updateDocError(Long docId, String errorMessage) {
        knowledgeDocumentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("ERROR");
            d.setErrorMessage(errorMessage);
            knowledgeDocumentRepository.save(d);
        });
    }

    @Transactional
    public void updateDocSuccess(Long docId, int chunkCount) {
        knowledgeDocumentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("COMPLETED");
            d.setChunkCount(chunkCount);
            knowledgeDocumentRepository.save(d);
        });
    }

    @Transactional
    public void incrementKbDocCount(Long kbId) {
        knowledgeBaseRepository.findById(kbId).ifPresent(k -> {
            k.setDocumentCount(k.getDocumentCount() != null ? k.getDocumentCount() + 1 : 1);
            knowledgeBaseRepository.save(k);
        });
    }

    @Transactional
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + id));

        if (dynamicVectorStoreConfig.hasActiveConfig(kb.getVectorDbType())) {
            try {
                VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore(kb.getVectorDbType());
                List<KnowledgeDocument> docs = knowledgeDocumentRepository.findByKnowledgeBaseId(id);
                List<String> docIds = new ArrayList<>();
                for (KnowledgeDocument doc : docs) {
                    for (int i = 0; i < (doc.getChunkCount() != null ? doc.getChunkCount() : 0); i++) {
                        docIds.add("kb_" + id + "_doc_" + doc.getId() + "_chunk_" + i);
                    }
                }
                if (!docIds.isEmpty()) {
                    vectorStore.delete(docIds);
                }
            } catch (Exception e) {
                log.warn("删除向量数据失败: {}", e.getMessage());
            }
        }

        knowledgeDocumentRepository.deleteAll(knowledgeDocumentRepository.findByKnowledgeBaseId(id));
        knowledgeBaseRepository.delete(kb);
    }

    @Transactional
    public void deleteDocument(Long knowledgeBaseId, Long docId) {
        KnowledgeDocument doc = knowledgeDocumentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));
        if (!doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new IllegalArgumentException("文档不属于该知识库");
        }

        // 删除向量数据
        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
        if (dynamicVectorStoreConfig.hasActiveConfig(kb.getVectorDbType())) {
            try {
                VectorStore vectorStore = dynamicVectorStoreConfig.getVectorStore(kb.getVectorDbType());
                List<String> docIds = new ArrayList<>();
                for (int i = 0; i < (doc.getChunkCount() != null ? doc.getChunkCount() : 0); i++) {
                    docIds.add("kb_" + knowledgeBaseId + "_doc_" + docId + "_chunk_" + i);
                }
                if (!docIds.isEmpty()) {
                    vectorStore.delete(docIds);
                }
            } catch (Exception e) {
                log.warn("删除文档向量数据失败: {}", e.getMessage());
            }
        }

        // 删除磁盘文件
        if (doc.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(doc.getFilePath()));
            } catch (Exception e) {
                log.warn("删除文件失败: {}", e.getMessage());
            }
        }

        knowledgeDocumentRepository.delete(doc);

        // 更新知识库文档计数
        knowledgeBaseRepository.findById(knowledgeBaseId).ifPresent(k -> {
            k.setDocumentCount(Math.max(0, (k.getDocumentCount() != null ? k.getDocumentCount() : 1) - 1));
            knowledgeBaseRepository.save(k);
        });
    }

    public Path getDocumentFilePath(Long knowledgeBaseId, Long docId) {
        KnowledgeDocument doc = knowledgeDocumentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));
        if (!doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new IllegalArgumentException("文档不属于该知识库");
        }
        if (doc.getFilePath() == null || !Files.exists(Paths.get(doc.getFilePath()))) {
            throw new IllegalStateException("文件不存在或已被清理");
        }
        return Paths.get(doc.getFilePath());
    }

    private KnowledgeBaseDTO toDTO(KnowledgeBase kb) {
        return KnowledgeBaseDTO.builder()
                .id(kb.getId())
                .userId(kb.getUserId())
                .name(kb.getName())
                .type(kb.getType())
                .systemPrompt(kb.getSystemPrompt())
                .databaseName(kb.getDatabaseName())
                .jdbcUrl(kb.getJdbcUrl())
                .dbUsername(kb.getDbUsername())
                .dbPassword(kb.getDbPassword())
                .vectorDbType(kb.getVectorDbType())
                .vectorCollection(kb.getVectorCollection())
                .vectorDbConfigId(kb.getVectorDbConfigId())
                .description(kb.getDescription())
                .documentCount(kb.getDocumentCount())
                .status(kb.getStatus())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "unknown";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
