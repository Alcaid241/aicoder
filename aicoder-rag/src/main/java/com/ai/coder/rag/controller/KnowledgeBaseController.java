package com.ai.coder.rag.controller;

import com.ai.coder.rag.dto.KnowledgeBaseCreateRequest;
import com.ai.coder.rag.dto.KnowledgeBaseDTO;
import com.ai.coder.rag.dto.KnowledgeBaseUpdateRequest;
import com.ai.coder.rag.entity.KnowledgeDocument;
import com.ai.coder.rag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public ResponseEntity<KnowledgeBaseDTO> create(
            @RequestBody KnowledgeBaseCreateRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(knowledgeBaseService.createKnowledgeBase(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeBaseDTO>> list(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(knowledgeBaseService.listKnowledgeBases(userId));
    }

    @PostMapping("/{id}/upload")
    public ResponseEntity<KnowledgeDocument> upload(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        System.err.println("=== [RAG] 收到上传请求: kbId=" + id + ", file=" + file.getOriginalFilename() + ", size=" + file.getSize());
        long start = System.currentTimeMillis();
        try {
            KnowledgeDocument result = knowledgeBaseService.uploadDocument(id, file);
            System.err.println("=== [RAG] 上传完成, 耗时: " + (System.currentTimeMillis() - start) + "ms");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("=== [RAG] 上传失败: " + e.getMessage());
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBaseDTO> update(
            @PathVariable Long id,
            @RequestBody KnowledgeBaseUpdateRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(knowledgeBaseService.updateKnowledgeBase(id, request, userId));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<List<KnowledgeDocument>> getDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(knowledgeBaseService.getDocuments(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @DeleteMapping("/{kbId}/docs/{docId}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        knowledgeBaseService.deleteDocument(kbId, docId);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/{kbId}/docs/{docId}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        try {
            Path filePath = knowledgeBaseService.getDocumentFilePath(kbId, docId);
            Resource resource = new UrlResource(filePath.toUri());
            String encodedName = URLEncoder.encode(filePath.getFileName().toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                    .body(resource);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "文件下载失败: " + e.getMessage()));
        }
    }
}
