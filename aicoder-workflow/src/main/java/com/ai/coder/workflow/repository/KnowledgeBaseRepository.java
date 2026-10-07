package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByUserId(Long userId);

    List<KnowledgeBase> findByType(String type);
}
