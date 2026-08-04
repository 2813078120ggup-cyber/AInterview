package com.tyut.aiinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deepseek")
public record DeepSeekProperties(boolean enabled, String apiKey, String baseUrl, String model) {
    public DeepSeekProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.deepseek.com";
        if (model == null || model.isBlank()) model = "deepseek-v4-flash";
    }
    public boolean configured() { return enabled && apiKey != null && !apiKey.isBlank(); }
}
