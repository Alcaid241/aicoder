package com.ai.coder.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoDTO {

    private String modelId;
    private String modelName;
    private String provider;
    private String description;
}
