package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {
    List<WorkflowExecution> findByWorkflowIdOrderByStartedAtDesc(Long workflowId);

    long countByUserId(Long userId);
}
