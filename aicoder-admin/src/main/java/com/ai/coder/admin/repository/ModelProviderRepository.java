package com.ai.coder.admin.repository;

import com.ai.coder.admin.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    Optional<ModelProvider> findByCode(String code);
    boolean existsByCode(String code);
    List<ModelProvider> findByEnabledOrderBySortAsc(Integer enabled);
}
