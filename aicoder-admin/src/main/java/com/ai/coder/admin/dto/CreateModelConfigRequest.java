package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateModelConfigRequest {

    private Long providerId;
    private String displayName;
    private String modelCode;
    private String modelType;
    private Integer enabled;
    private Integer sort;
    private Integer supportTools = 0;
}
