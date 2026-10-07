package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDTO {

    private Long id;
    private Long providerId;
    private String providerName;
    private String displayName;
    private String modelCode;
    private String modelType;
    private Integer enabled;
    private Integer sort;
}
