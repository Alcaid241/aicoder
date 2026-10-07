package com.ai.coder.admin.repository;

import com.ai.coder.admin.entity.VectorDbConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VectorDbConfigRepository extends JpaRepository<VectorDbConfig, Long> {

    List<VectorDbConfig> findByActiveTrue();

    List<VectorDbConfig> findByDbTypeAndActiveTrue(String dbType);

    @Modifying
    @Query("UPDATE VectorDbConfig v SET v.active = false WHERE v.dbType = :dbType AND v.active = true")
    void deactivateByDbType(String dbType);
}
