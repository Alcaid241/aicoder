package com.ai.coder.skill.exception;

/** 技能名重复，映射 HTTP 409 Conflict。 */
public class DuplicateSkillException extends RuntimeException {
    public DuplicateSkillException(String message) {
        super(message);
    }
}
