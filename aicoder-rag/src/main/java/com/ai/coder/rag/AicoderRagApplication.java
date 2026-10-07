package com.ai.coder.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@ComponentScan(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
@SpringBootApplication
@EntityScan(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.rag", "com.ai.coder.core"})
@EnableDiscoveryClient
public class AicoderRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderRagApplication.class, args);
    }
}
