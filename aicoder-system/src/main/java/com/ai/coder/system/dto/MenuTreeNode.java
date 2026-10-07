package com.ai.coder.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuTreeNode {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sort;
    private Integer type;
    private List<MenuTreeNode> children;
}
