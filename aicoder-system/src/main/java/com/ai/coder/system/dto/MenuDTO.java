package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuDTO {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    private Integer type;
}
