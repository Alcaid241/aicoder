package com.ai.coder.skill.config;

import com.ai.coder.skill.exception.DuplicateSkillException;
import com.ai.coder.skill.exception.IllegalSkillStateException;
import com.ai.coder.skill.exception.SkillNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> r) {
        return (Map<String, Object>) r.getBody();
    }

    @Test
    void skill_not_found_maps_404() {
        ResponseEntity<?> r = handler.handleNotFound(new SkillNotFoundException("技能不存在：999"));

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals(404, body(r).get("code"));
        assertEquals("技能不存在：999", body(r).get("message"));
    }

    @Test
    void duplicate_skill_maps_409() {
        ResponseEntity<?> r = handler.handleConflict(new DuplicateSkillException("技能名已存在：faq-skill"));

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(409, body(r).get("code"));
        assertEquals("技能名已存在：faq-skill", body(r).get("message"));
    }

    @Test
    void illegal_state_transition_maps_409() {
        ResponseEntity<?> r = handler.handleConflict(new IllegalSkillStateException("非法的状态迁移：ACTIVE -> ACTIVE"));

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(409, body(r).get("code"));
    }

    @Test
    void unexpected_exception_maps_500_with_generic_message() {
        ResponseEntity<?> r = handler.handleUnexpected(new NullPointerException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals(500, body(r).get("code"));
        // 内部异常消息不得外泄，统一返回通用文案
        assertEquals("服务内部错误", body(r).get("message"));
    }
}
