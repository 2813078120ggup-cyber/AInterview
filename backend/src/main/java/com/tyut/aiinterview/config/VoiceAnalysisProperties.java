package com.tyut.aiinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音分析服务（独立轻量 Python 服务）的连接配置。
 *
 * <p>语音分析感知模块由独立的 Python 服务（voice-analysis-service）承载，
 * Java 后端通过 HTTP 调用。未启用时不影响现有文本评分链路。
 */
@ConfigurationProperties(prefix = "app.voice-analysis")
public record VoiceAnalysisProperties(
        boolean enabled,
        String baseUrl,
        String internalToken,
        int connectTimeoutMs,
        int readTimeoutMs,
        boolean reportVoiceDimension) {

    public VoiceAnalysisProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://127.0.0.1:8500";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 2_000;
        if (readTimeoutMs <= 0) readTimeoutMs = 20_000;
    }

    /** 服务是否可用（启用且配置了地址）。 */
    public boolean configured() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }
}
