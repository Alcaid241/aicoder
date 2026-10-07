package com.ai.coder.skill.config;

import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import com.ai.coder.skill.exception.SkillNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(SkillNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler({DuplicateSkillException.class, IllegalSkillStateException.class})
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        // 内部异常细节不得外泄，统一返回通用文案
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务内部错误"));
    }

    private static Map<String, Object> body(int code, String message) {
        return Map.of("code", code, "message", message != null ? message : "未知错误");
    }
}
