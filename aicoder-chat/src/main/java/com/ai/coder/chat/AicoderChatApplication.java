package com.ai.coder.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@ComponentScan(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.chat", "com.ai.coder.core"})
@EnableDiscoveryClient
public class AicoderChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderChatApplication.class, args);
    }
}
