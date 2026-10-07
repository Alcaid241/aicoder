package com.ai.coder.mcp.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

/**
 * 两个 RestTemplate：
 * - loadBalancedRestTemplate：解析服务名（http://aicoder-skill/...）供 SkillTool 调 skill 服务；
 * - plainRestTemplate：直连外部 URL（搜索 API、网页抓取），应用 fetch 超时防挂起阻塞。
 */
@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

    private final McpProperties props;

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate plainRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getFetch().getConnectTimeout());
        factory.setReadTimeout(props.getFetch().getReadTimeout());
        return new RestTemplate(factory);
    }
}
