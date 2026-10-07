package com.ai.coder.skill.exception;

/** 技能不存在，映射 HTTP 404。 */
public class SkillNotFoundException extends RuntimeException {
    public SkillNotFoundException(String message) {
        super(message);
    }
}
