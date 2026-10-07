package com.ai.coder.core.web;

import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模型注册表热重载端点（内部，不经 Gateway）。
 * 一份 controller 服务 chat/rag/workflow——各 app 经 @ComponentScan("com.ai.coder.core") 扫描到此 bean。
 * 由 admin 在模型配置变更后通过 @LoadBalanced RestTemplate 调用，实现"启用即生效"。
 */
@RestController
@RequestMapping("/internal/registry")
@RequiredArgsConstructor
public class RegistryReloadController {

    private final AbstractDynamicModelRegistry registry;

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        registry.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
