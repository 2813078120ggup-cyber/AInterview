package com.tyut.aiinterview.algorithm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.algorithm.judge")
public class AlgorithmJudgeProperties {
    private boolean enabled = true;
    private boolean consumerEnabled = true;
    private String runnerImage = "algorithm-runner-java17:latest";
    /** Empty means auto-detect: npipe on Windows, unix socket elsewhere. */
    private String dockerHost = "";
    private long outputLimitBytes = 8L * 1024 * 1024;
    private int sourceLimitChars = 100_000;
    private int maxTestCases = 50;
    private int compileTimeoutSeconds = 30;
    private int cpuCount = 1;
}
