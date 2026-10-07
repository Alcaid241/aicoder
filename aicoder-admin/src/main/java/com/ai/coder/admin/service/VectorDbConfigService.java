package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.VectorDbConfigDTO;
import com.ai.coder.admin.entity.VectorDbConfig;
import com.ai.coder.admin.repository.VectorDbConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VectorDbConfigService {

    private final VectorDbConfigRepository vectorDbConfigRepository;

    public List<VectorDbConfig> listAll() {
        return vectorDbConfigRepository.findAll();
    }

    public VectorDbConfig getById(Long id) {
        return vectorDbConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在"));
    }

    @Transactional
    public VectorDbConfig create(VectorDbConfigDTO dto) {
        VectorDbConfig config = new VectorDbConfig();
        config.setDbType(dto.getDbType());
        config.setHost(dto.getHost());
        config.setPort(dto.getPort());
        config.setDatabaseName(dto.getDatabaseName());
        config.setCollectionName(dto.getCollectionName());
        config.setExtraConfig(dto.getExtraConfig());
        config.setActive(dto.getActive() != null ? dto.getActive() : false);
        config.setDescription(dto.getDescription());
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return vectorDbConfigRepository.save(config);
    }

    @Transactional
    public VectorDbConfig update(Long id, VectorDbConfigDTO dto) {
        VectorDbConfig config = getById(id);
        config.setDbType(dto.getDbType());
        config.setHost(dto.getHost());
        config.setPort(dto.getPort());
        config.setDatabaseName(dto.getDatabaseName());
        config.setCollectionName(dto.getCollectionName());
        config.setExtraConfig(dto.getExtraConfig());
        config.setDescription(dto.getDescription());
        config.setUpdatedAt(LocalDateTime.now());
        return vectorDbConfigRepository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        vectorDbConfigRepository.deleteById(id);
    }

    @Transactional
    public VectorDbConfig activate(Long id) {
        VectorDbConfig config = getById(id);
        vectorDbConfigRepository.deactivateByDbType(config.getDbType());
        config.setActive(true);
        config.setUpdatedAt(LocalDateTime.now());
        return vectorDbConfigRepository.save(config);
    }

    public Optional<VectorDbConfig> getActiveConfig() {
        List<VectorDbConfig> activeList = vectorDbConfigRepository.findByActiveTrue();
        return activeList.stream().findFirst();
    }

    public List<VectorDbConfig> getActiveConfigs() {
        return vectorDbConfigRepository.findByActiveTrue();
    }
}
