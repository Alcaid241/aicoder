package com.ai.coder.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @org.springframework.cloud.client.loadbalancer.LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
