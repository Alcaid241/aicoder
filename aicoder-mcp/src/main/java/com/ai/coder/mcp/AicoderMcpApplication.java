package com.ai.coder.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** aicoder-mcp：MCP Server。@EntityScan/@EnableJpaRepositories 含 core 包以读取 ai_model_config（多模态模型）。 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EntityScan(basePackages = {"com.ai.coder.mcp", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.mcp", "com.ai.coder.core"})
public class AicoderMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderMcpApplication.class, args);
    }
}
