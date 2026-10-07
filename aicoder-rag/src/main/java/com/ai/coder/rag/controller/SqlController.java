package com.ai.coder.rag.controller;

import com.ai.coder.rag.dto.SqlAskRequest;
import com.ai.coder.rag.dto.SqlAskResponse;
import com.ai.coder.rag.service.SqlExecutionService;
import com.ai.coder.rag.service.SqlGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag/sql")
@RequiredArgsConstructor
public class SqlController {

    private final SqlGenerationService sqlGenerationService;
    private final SqlExecutionService sqlExecutionService;

    @PostMapping("/ask")
    public ResponseEntity<SqlAskResponse> ask(@RequestBody SqlAskRequest request) {
        return ResponseEntity.ok(sqlGenerationService.generateSql(request));
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        String databaseName = body.get("databaseName");
        String jdbcUrl = body.get("jdbcUrl");
        String dbUsername = body.get("dbUsername");
        String dbPassword = body.get("dbPassword");
        return ResponseEntity.ok(sqlExecutionService.executeSql(sql, databaseName, jdbcUrl, dbUsername, dbPassword));
    }
}
