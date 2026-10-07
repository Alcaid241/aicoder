package com.ai.coder.admin.service;

import com.ai.coder.admin.dto.CreateProviderRequest;
import com.ai.coder.admin.dto.ProviderDTO;
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
public class ModelProviderService {

    private final ModelProviderRepository modelProviderRepository;
    private final ModelConfigRepository modelConfigRepository;

    public List<ProviderDTO> listProviders() {
        return modelProviderRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ProviderDTO getById(Long id) {
        ModelProvider provider = modelProviderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("厂商不存在"));
        return toDTO(provider);
    }

    @Transactional
    public ProviderDTO create(CreateProviderRequest request) {
        if (modelProviderRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("厂商编码已存在: " + request.getCode());
        }
        ModelProvider provider = new ModelProvider();
        provider.setName(request.getName());
        provider.setLogo(request.getLogo());
        provider.setCode(request.getCode());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setApiKey(request.getApiKey());
        provider.setDescription(request.getDescription());
        provider.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        provider.setSort(request.getSort() != null ? request.getSort() : 0);
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        return toDTO(modelProviderRepository.save(provider));
    }

    @Transactional
    public ProviderDTO update(Long id, CreateProviderRequest request) {
        ModelProvider provider = modelProviderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("厂商不存在"));
        if (request.getName() != null) {
            provider.setName(request.getName());
        }
        if (request.getLogo() != null) {
            provider.setLogo(request.getLogo());
        }
        if (request.getCode() != null) {
            provider.setCode(request.getCode());
        }
        if (request.getBaseUrl() != null) {
            provider.setBaseUrl(request.getBaseUrl());
        }
        if (request.getApiKey() != null) {
            provider.setApiKey(request.getApiKey());
        }
        if (request.getDescription() != null) {
            provider.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            provider.setEnabled(request.getEnabled());
        }
        if (request.getSort() != null) {
            provider.setSort(request.getSort());
        }
        provider.setUpdatedAt(LocalDateTime.now());
        return toDTO(modelProviderRepository.save(provider));
    }

    @Transactional
    public void delete(Long id) {
        if (modelConfigRepository.existsByProviderId(id)) {
            throw new RuntimeException("该厂商下存在关联模型，无法删除");
        }
        modelProviderRepository.deleteById(id);
    }

    private ProviderDTO toDTO(ModelProvider provider) {
        ProviderDTO dto = new ProviderDTO();
        dto.setId(provider.getId());
        dto.setName(provider.getName());
        dto.setLogo(provider.getLogo());
        dto.setCode(provider.getCode());
        dto.setBaseUrl(provider.getBaseUrl());
        dto.setApiKey(provider.getApiKey());
        dto.setDescription(provider.getDescription());
        dto.setEnabled(provider.getEnabled());
        dto.setSort(provider.getSort());
        return dto;
    }
}
