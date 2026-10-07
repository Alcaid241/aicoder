package com.ai.coder.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlAskResponse {
    private String status;
    private String sql;
    private String clarification;
    private List<String> options;
    private Boolean multiSelect;
    private Boolean isRejected;
    private Boolean canExecute;
}
