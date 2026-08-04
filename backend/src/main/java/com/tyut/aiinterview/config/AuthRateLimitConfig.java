package com.tyut.aiinterview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfig {
}
