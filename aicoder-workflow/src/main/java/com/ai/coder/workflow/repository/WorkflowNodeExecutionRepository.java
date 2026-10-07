package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.WorkflowNodeExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNodeExecutionRepository extends JpaRepository<WorkflowNodeExecution, Long> {
    List<WorkflowNodeExecution> findByExecutionIdOrderByStartedAtAsc(Long executionId);
}
