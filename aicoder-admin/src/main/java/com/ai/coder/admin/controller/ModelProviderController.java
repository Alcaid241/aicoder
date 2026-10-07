package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.CreateProviderRequest;
import com.ai.coder.admin.dto.ProviderDTO;
import com.ai.coder.admin.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model/provider")
@RequiredArgsConstructor
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    @GetMapping("/list")
    public List<ProviderDTO> list() {
        return modelProviderService.listProviders();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProviderDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(modelProviderService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ProviderDTO> create(@RequestBody CreateProviderRequest request) {
        return ResponseEntity.ok(modelProviderService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProviderDTO> update(@PathVariable Long id, @RequestBody CreateProviderRequest request) {
        return ResponseEntity.ok(modelProviderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        modelProviderService.delete(id);
        return ResponseEntity.ok().build();
    }
}
