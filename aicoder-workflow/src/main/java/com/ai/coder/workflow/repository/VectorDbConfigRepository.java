package com.ai.coder.workflow.repository;

import com.ai.coder.workflow.model.entity.VectorDbConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VectorDbConfigRepository extends JpaRepository<VectorDbConfig, Long> {

    Optional<VectorDbConfig> findByActiveTrue();

    @Modifying
    @Query("UPDATE VectorDbConfig v SET v.active = false WHERE v.active = true")
    void deactivateAll();
}
