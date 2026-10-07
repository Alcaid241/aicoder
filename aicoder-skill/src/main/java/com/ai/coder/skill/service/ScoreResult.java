package com.ai.coder.skill.service;

/** 启发式打分结果：0–100 分 + 扣分原因。 */
public record ScoreResult(int score, String rationale) {
}
