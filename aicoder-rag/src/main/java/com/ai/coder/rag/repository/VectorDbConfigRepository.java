package com.ai.coder.rag.repository;

import com.ai.coder.rag.entity.VectorDbConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VectorDbConfigRepository extends JpaRepository<VectorDbConfig, Long> {

    Optional<VectorDbConfig> findByActiveTrue();

    Optional<VectorDbConfig> findByDbTypeAndActiveTrue(String dbType);
}
