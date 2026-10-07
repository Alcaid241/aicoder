package com.ai.coder.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableAsync
public class AicoderAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderAdminApplication.class, args);
    }
}
