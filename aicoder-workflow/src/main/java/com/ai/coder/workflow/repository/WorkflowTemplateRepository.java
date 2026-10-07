package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {
    List<WorkflowTemplate> findByIsSystemOrUserIdOrderByCreatedAtDesc(Integer isSystem, Long userId);
}
