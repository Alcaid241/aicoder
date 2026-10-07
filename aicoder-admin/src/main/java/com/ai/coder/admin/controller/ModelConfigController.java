package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.CreateModelConfigRequest;
import com.ai.coder.admin.dto.ModelConfigDTO;
import com.ai.coder.admin.service.ModelConfigService;
import com.ai.coder.admin.service.RegistryReloadNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model/config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final RegistryReloadNotifier registryReloadNotifier;

    @GetMapping("/list")
    public List<ModelConfigDTO> list(@RequestParam(required = false) String modelType) {
        return modelConfigService.listModels(modelType);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelConfigDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(modelConfigService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ModelConfigDTO> create(@RequestBody CreateModelConfigRequest request) {
        ModelConfigDTO result = modelConfigService.create(request);
        registryReloadNotifier.reloadAll();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfigDTO> update(@PathVariable Long id, @RequestBody CreateModelConfigRequest request) {
        ModelConfigDTO result = modelConfigService.update(id, request);
        registryReloadNotifier.reloadAll();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        registryReloadNotifier.reloadAll();
        return ResponseEntity.ok().build();
    }
}
