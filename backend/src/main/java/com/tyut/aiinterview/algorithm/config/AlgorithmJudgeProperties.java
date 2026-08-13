package com.tyut.aiinterview.algorithm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.algorithm.judge")
public class AlgorithmJudgeProperties {
    private boolean enabled = true;
    private int sourceLimitChars = 100_000;
    /** Internal Worker endpoint used by synchronous custom-input runs. */
    private String workerBaseUrl = "http://127.0.0.1:8084/api";
    /** Eureka service id used when backend-to-Worker discovery is enabled. */
    private String workerServiceId = "algorithm-judge-worker";
    /** Enables LoadBalancer selection; local development can keep direct mode. */
    private boolean workerDiscoveryEnabled = false;
    /** Context path exposed by the Worker HTTP API. */
    private String workerServicePath = "/api";
    private int workerConnectTimeoutMs = 2_000;
    private int workerReadTimeoutMs = 30_000;
    private String workerCircuitBreakerName = "algorithmJudgeWorker";
    private int workerBulkheadMaxConcurrent = 8;
    private int workerBulkheadMaxWaitMs = 0;
    /** Optional shared secret for the private backend-to-Worker HTTP call. */
    private String internalToken = "";
    /** Optional mode-0600 file used when the secret is injected by Docker/KMS. */
    private String internalTokenFile = "/run/secrets/algorithm-judge-internal-token";
    /** Production can require a non-empty shared secret before the app starts. */
    private boolean requireInternalToken = false;
}
