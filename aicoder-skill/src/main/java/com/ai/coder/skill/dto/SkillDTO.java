package com.ai.coder.skill.dto;

import com.ai.coder.skill.entity.SkillSource;
import com.ai.coder.skill.entity.SkillStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SkillDTO {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String content;
    private Integer version;
    private SkillStatus status;
    private SkillSource source;
    private String category;
    private String tags;
    private BigDecimal qualityScore;
    private String trialResult;
    private Long parentSkillId;
    private Long authorUserId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
