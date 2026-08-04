package com.tyut.aiinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload-security")
public record UploadSecurityProperties(boolean clamavRequired, String clamavHost, int clamavPort) {
    public UploadSecurityProperties {
        if (clamavPort <= 0) clamavPort = 3310;
    }
}
