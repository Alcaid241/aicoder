package com.ai.coder.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorDbConfigDTO {

    private Long id;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String collectionName;
    private String extraConfig;
    private Boolean active;
    private String description;
}
