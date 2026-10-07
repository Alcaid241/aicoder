package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    List<Workflow> findByUserIdOrderByUpdatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
