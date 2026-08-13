package com.tyut.aiinterview.algorithmworker.config;

import lombok.Data;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.algorithm.judge")
public class AlgorithmJudgeProperties {
    private boolean enabled = true;
    private boolean consumerEnabled = true;
    private String consumerName = "judge-consumer-1";
    private String runnerImage = "algorithm-runner-java17:latest";
    /** Empty means auto-detect: npipe on Windows, unix socket elsewhere. */
    private String dockerHost = "";
    private long outputLimitBytes = 8L * 1024 * 1024;
    private int sourceLimitChars = 100_000;
    private int maxTestCases = 50;
    private int compileTimeoutSeconds = 30;
    private int cpuCount = 1;
    private String internalToken = "";
    private String internalTokenFile = "/run/secrets/algorithm-judge-internal-token";
    private boolean requireInternalToken = false;

    public String resolveInternalToken() {
        if (internalToken != null && !internalToken.isBlank()) {
            return internalToken.trim();
        }
        if (internalTokenFile == null || internalTokenFile.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(internalTokenFile);
            return Files.isRegularFile(path) ? Files.readString(path).trim() : "";
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }
}
