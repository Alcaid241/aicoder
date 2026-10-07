package com.ai.coder.admin.repository;

import com.ai.coder.admin.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByProviderIdOrderBySortAsc(Long providerId);
    List<ModelConfig> findByModelTypeAndEnabledOrderBySortAsc(String modelType, Integer enabled);
    List<ModelConfig> findByEnabledOrderBySortAsc(Integer enabled);
    boolean existsByProviderId(Long providerId);
}
