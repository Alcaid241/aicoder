package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.VectorDbConfigDTO;
import com.ai.coder.admin.entity.VectorDbConfig;
import com.ai.coder.admin.service.VectorDbConfigService;
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
@RequestMapping("/api/admin/vector-db-config")
@RequiredArgsConstructor
public class VectorDbConfigController {

    private final VectorDbConfigService vectorDbConfigService;

    @GetMapping
    public ResponseEntity<List<VectorDbConfig>> list() {
        return ResponseEntity.ok(vectorDbConfigService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VectorDbConfig> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vectorDbConfigService.getById(id));
    }

    @PostMapping
    public ResponseEntity<VectorDbConfig> create(@RequestBody VectorDbConfigDTO dto) {
        return ResponseEntity.ok(vectorDbConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VectorDbConfig> update(@PathVariable Long id, @RequestBody VectorDbConfigDTO dto) {
        return ResponseEntity.ok(vectorDbConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vectorDbConfigService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<VectorDbConfig> activate(@PathVariable Long id) {
        return ResponseEntity.ok(vectorDbConfigService.activate(id));
    }
}
