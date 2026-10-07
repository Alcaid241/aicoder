package com.ai.coder.skill.dto;

import lombok.Data;

/** 「生成技能」入口载荷：用户描述的技能意图。author 取自 X-User-Id 头。 */
@Data
public class GenerateSkillRequest {
    private String intent;
}
