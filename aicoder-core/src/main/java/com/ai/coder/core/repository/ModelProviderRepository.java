package com.ai.coder.core.repository;

import com.ai.coder.core.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    List<ModelProvider> findByEnabledOrderBySortAsc(Integer enabled);
}
