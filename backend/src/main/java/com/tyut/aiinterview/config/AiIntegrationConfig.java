package com.tyut.aiinterview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, DeepSeekProperties.class, UploadSecurityProperties.class})
public class AiIntegrationConfig {
}
