package com.tyut.aiinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String rootDirectory, long maxUploadBytes) {
    public StorageProperties {
        if (rootDirectory == null || rootDirectory.isBlank()) rootDirectory = "./data/media";
        if (maxUploadBytes <= 0) maxUploadBytes = 104_857_600L;
    }
}
