package com.ai.coder.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 模型配置变更后通知 chat/rag/workflow 热重载注册表（@Async fire-and-forget）。
 * 每服务独立 try/catch：某服务挂/失败只记日志，不影响 admin 保存与其它服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryReloadNotifier {

    private static final List<String> SERVICES = List.of("aicoder-chat", "aicoder-rag", "aicoder-workflow");

    private final RestTemplate restTemplate;

    @Async
    public void reloadAll() {
        for (String service : SERVICES) {
            try {
                restTemplate.postForObject(
                        "http://" + service + "/internal/registry/reload", null, Object.class);
                log.info("已通知 {} 重载模型注册表", service);
            } catch (Exception e) {
                log.warn("通知 {} 重载模型注册表失败（不影响保存，下次重启补偿）：{}", service, e.getMessage());
            }
        }
    }
}
