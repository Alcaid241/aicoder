package com.ai.coder.skill.dto;

import lombok.Data;

/** submit_skill_draft 工具的写回载荷。author 取自请求头 X-User-Id（由 chat 工具透传）。 */
@Data
public class SkillDraftRequest {
    private String name;
    private String description;
    private String content;
}
