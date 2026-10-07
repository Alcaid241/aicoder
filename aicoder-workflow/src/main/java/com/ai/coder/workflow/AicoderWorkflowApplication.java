package com.ai.coder.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@ComponentScan(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.workflow", "com.ai.coder.core"})
public class AicoderWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(AicoderWorkflowApplication.class, args);
    }
}
