package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDTO {

    private Long id;
    private String name;
    private String logo;
    private String code;
    private String baseUrl;
    private String apiKey;
    private String description;
    private Integer enabled;
    private Integer sort;
}
