package com.ai.coder.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class DocumentParserService {

    private static final int MAX_WRITE_LIMIT = 500_000;
    private static final Set<String> TEXT_TYPES = Set.of("txt", "md", "csv", "json", "log", "sql", "java", "py", "js", "ts", "xml", "yaml", "yml", "properties");

    private final Parser parser = new AutoDetectParser();

    public String parseDocument(MultipartFile file, String fileType) throws IOException {
        log.info("开始解析文档: {}, 大小: {} bytes, 类型: {}", file.getOriginalFilename(), file.getSize(), fileType);

        if (TEXT_TYPES.contains(fileType)) {
            return parseTextFile(file);
        }

        return parseWithTika(file);
    }

    private String parseTextFile(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (content.length() > MAX_WRITE_LIMIT) {
            log.warn("文本内容过长({}字符)，截断到{}字符", content.length(), MAX_WRITE_LIMIT);
            content = content.substring(0, MAX_WRITE_LIMIT);
        }
        log.info("文本文件直接读取完成，长度: {}", content.length());
        return content;
    }

    private String parseWithTika(MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("tikaparse_", "." + getExtension(file.getOriginalFilename()));
        try {
            file.transferTo(tempFile.toFile());
            log.info("文件已写入临时文件: {}, 大小: {} bytes", tempFile, Files.size(tempFile));

            try (FileInputStream fis = new FileInputStream(tempFile.toFile())) {
                BodyContentHandler handler = new BodyContentHandler(MAX_WRITE_LIMIT);
                Metadata metadata = new Metadata();
                metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
                ParseContext context = new ParseContext();
                context.set(Parser.class, parser);

                parser.parse(fis, handler, metadata, context);

                String result = handler.toString();
                log.info("Tika解析完成，文本长度: {}", result != null ? result.length() : 0);
                return result != null ? result : "";
            } catch (Exception e) {
                if (e instanceof SAXException && e.getMessage() != null && e.getMessage().contains("write limit")) {
                    throw new RuntimeException("文档内容过大，请上传较小的文件");
                }
                throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }
        }
    }

    public List<String> splitToChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.replaceAll("\\s+", " ").trim();
        int textLength = text.length();
        int start = 0;

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            if (end < textLength) {
                int lastPeriod = text.lastIndexOf('。', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int lastSpace = text.lastIndexOf(' ', end);
                int breakPoint = Math.max(Math.max(lastPeriod, lastNewline), lastSpace);

                if (breakPoint > start) {
                    end = breakPoint + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            int nextStart = end - overlap;
            if (nextStart <= start) {
                nextStart = end;
            }
            if (nextStart >= textLength) break;
            start = nextStart;
        }

        return chunks;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "tmp";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
