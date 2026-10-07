package com.ai.coder.workflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowCreateRequest {
    @NotBlank(message = "工作流名称不能为空")
    private String name;
    private String description;
    private String type;
    private String replyRequirements;
    @NotBlank(message = "画布数据不能为空")
    private String graphData;
}
