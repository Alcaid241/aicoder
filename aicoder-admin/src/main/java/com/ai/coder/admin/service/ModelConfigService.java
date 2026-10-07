package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.CreateModelConfigRequest;
import com.ai.coder.admin.dto.ModelConfigDTO;
import com.ai.coder.admin.entity.ModelConfig;
import com.ai.coder.admin.entity.ModelProvider;
import com.ai.coder.admin.repository.ModelConfigRepository;
import com.ai.coder.admin.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final ModelProviderRepository modelProviderRepository;

    public List<ModelConfigDTO> listModels(String modelType) {
        if (modelType != null && !modelType.isEmpty()) {
            return modelConfigRepository.findByModelTypeAndEnabledOrderBySortAsc(modelType, 1).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
        return modelConfigRepository.findByEnabledOrderBySortAsc(1).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ModelConfigDTO> listAllModels() {
        return modelConfigRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ModelConfigDTO getById(Long id) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
        return toDTO(config);
    }

    @Transactional
    public ModelConfigDTO create(CreateModelConfigRequest request) {
        modelProviderRepository.findById(request.getProviderId())
                .orElseThrow(() -> new RuntimeException("厂商不存在"));
        ModelConfig config = new ModelConfig();
        config.setProviderId(request.getProviderId());
        config.setDisplayName(request.getDisplayName());
        config.setModelCode(request.getModelCode());
        config.setModelType(request.getModelType());
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        config.setSort(request.getSort() != null ? request.getSort() : 0);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return toDTO(modelConfigRepository.save(config));
    }

    @Transactional
    public ModelConfigDTO update(Long id, CreateModelConfigRequest request) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
        if (request.getProviderId() != null) {
            modelProviderRepository.findById(request.getProviderId())
                    .orElseThrow(() -> new RuntimeException("厂商不存在"));
            config.setProviderId(request.getProviderId());
        }
        if (request.getDisplayName() != null) {
            config.setDisplayName(request.getDisplayName());
        }
        if (request.getModelCode() != null) {
            config.setModelCode(request.getModelCode());
        }
        if (request.getModelType() != null) {
            config.setModelType(request.getModelType());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getSort() != null) {
            config.setSort(request.getSort());
        }
        config.setUpdatedAt(LocalDateTime.now());
        return toDTO(modelConfigRepository.save(config));
    }

    @Transactional
    public void delete(Long id) {
        modelConfigRepository.deleteById(id);
    }

    private ModelConfigDTO toDTO(ModelConfig config) {
        ModelConfigDTO dto = new ModelConfigDTO();
        dto.setId(config.getId());
        dto.setProviderId(config.getProviderId());
        dto.setProviderName(modelProviderRepository.findById(config.getProviderId())
                .map(ModelProvider::getName)
                .orElse("未知"));
        dto.setDisplayName(config.getDisplayName());
        dto.setModelCode(config.getModelCode());
        dto.setModelType(config.getModelType());
        dto.setEnabled(config.getEnabled());
        dto.setSort(config.getSort());
        return dto;
    }
}
