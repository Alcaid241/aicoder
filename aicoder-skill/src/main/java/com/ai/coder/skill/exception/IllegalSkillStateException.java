package com.ai.coder.skill.exception;

/** 非法的生命周期状态迁移，映射 HTTP 409 Conflict。 */
public class IllegalSkillStateException extends RuntimeException {
    public IllegalSkillStateException(String message) {
        super(message);
    }
}
