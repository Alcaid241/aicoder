package com.ai.coder.core.repository;

import com.ai.coder.core.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.util.Optional;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByEnabledOrderBySortAsc(Integer enabled);
    Optional<ModelConfig> findByModelCodeAndEnabled(String modelCode, Integer enabled);
}
