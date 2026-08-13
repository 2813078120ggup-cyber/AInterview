package com.tyut.aiinterview.algorithm;

import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Backend-to-Worker internal client for synchronous custom-input runs.
 *
 * <p>The backend owns the submission record and authorization. The Worker owns
 * Docker access and returns only the execution result over the private network.
 */
@Component
public class AlgorithmJudgeWorkerClient {

    private final RestClient restClient;
    private final AlgorithmJudgeProperties properties;
    private final LoadBalancerClient loadBalancerClient;
    private final URI directBaseUri;
    private final String internalToken;
    private final CircuitBreaker circuitBreaker;
    private final Semaphore workerBulkhead;
    private final int workerBulkheadMaxConcurrent;
    private final Counter bulkheadRejectedCounter;

    public AlgorithmJudgeWorkerClient(RestClient.Builder builder,
                                     AlgorithmJudgeProperties properties,
                                     LoadBalancerClient loadBalancerClient) {
        this(builder, properties, loadBalancerClient, null, null);
    }

    public AlgorithmJudgeWorkerClient(RestClient.Builder builder,
                                     AlgorithmJudgeProperties properties,
                                     LoadBalancerClient loadBalancerClient,
                                     CircuitBreakerRegistry circuitBreakerRegistry) {
        this(builder, properties, loadBalancerClient, circuitBreakerRegistry, null);
    }

    @Autowired
    public AlgorithmJudgeWorkerClient(RestClient.Builder builder,
                                     AlgorithmJudgeProperties properties,
                                     LoadBalancerClient loadBalancerClient,
                                     CircuitBreakerRegistry circuitBreakerRegistry,
                                     MeterRegistry meterRegistry) {
        this.properties = properties;
        this.loadBalancerClient = loadBalancerClient;
        String baseUrl = Objects.requireNonNullElse(properties.getWorkerBaseUrl(),
                "http://127.0.0.1:8084/api");
        this.directBaseUri = URI.create(baseUrl);
        this.internalToken = resolveToken(properties.getInternalToken(), properties.getInternalTokenFile());
        if (properties.isRequireInternalToken() && internalToken.isBlank()) {
            throw new IllegalStateException("ALGORITHM_JUDGE_INTERNAL_TOKEN or its secret file must be configured");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeout(properties.getWorkerConnectTimeoutMs(), 2_000)));
        requestFactory.setReadTimeout(Duration.ofMillis(timeout(properties.getWorkerReadTimeoutMs(), 30_000)));
        this.restClient = builder.requestFactory(requestFactory).build();
        String circuitBreakerName = properties.getWorkerCircuitBreakerName();
        this.circuitBreaker = circuitBreakerRegistry == null
                ? null
                : circuitBreakerRegistry.circuitBreaker(
                        circuitBreakerName == null || circuitBreakerName.isBlank()
                                ? "algorithmJudgeWorker" : circuitBreakerName);
        this.workerBulkheadMaxConcurrent = Math.max(1, properties.getWorkerBulkheadMaxConcurrent());
        this.workerBulkhead = new Semaphore(workerBulkheadMaxConcurrent, true);
        if (meterRegistry == null) {
            this.bulkheadRejectedCounter = null;
        } else {
            this.bulkheadRejectedCounter = Counter.builder(
                            "ai.interview.algorithm.judge.worker.client.bulkhead.rejected")
                    .description("Backend synchronous Worker calls rejected by the concurrency bulkhead")
                    .register(meterRegistry);
            Gauge.builder("ai.interview.algorithm.judge.worker.client.bulkhead.active", workerBulkhead,
                            semaphore -> workerBulkheadMaxConcurrent - semaphore.availablePermits())
                    .description("Backend synchronous Worker calls currently inside the concurrency bulkhead")
                    .register(meterRegistry);
        }
    }

    public RunResult run(String sourceCode, String input, int timeLimitMs, int memoryLimitMb) {
        if (!tryAcquireBulkhead()) {
            if (bulkheadRejectedCounter != null) {
                bulkheadRejectedCounter.increment();
            }
            return systemError(new IllegalStateException("判题 Worker 并发隔离已满"));
        }
        try {
            if (circuitBreaker == null) {
                return execute(sourceCode, input, timeLimitMs, memoryLimitMb);
            }
            return circuitBreaker.executeSupplier(() -> execute(sourceCode, input, timeLimitMs, memoryLimitMb));
        } catch (RuntimeException exception) {
            return systemError(exception);
        } finally {
            workerBulkhead.release();
        }
    }

    private RunResult execute(String sourceCode, String input, int timeLimitMs, int memoryLimitMb) {
        URI endpoint = workerEndpoint();
        RestClient.RequestHeadersSpec<?> request = restClient.post()
                .uri(endpoint)
                .body(new RunRequest(sourceCode, input == null ? "" : input, timeLimitMs, memoryLimitMb));
        if (!internalToken.isBlank()) {
            request = request.header("X-Internal-Token", internalToken);
        }
        RunResponse response = request.retrieve().body(RunResponse.class);
        if (response == null) {
            throw new RestClientException("判题 Worker 返回空响应");
        }
        if (response.status() == null || response.status().isBlank()) {
            throw new RestClientException("判题 Worker 返回空状态");
        }
        if ("SYSTEM_ERROR".equalsIgnoreCase(response.status())) {
            throw new RestClientException("判题 Worker 返回 SYSTEM_ERROR: " + response.errorMessage());
        }
        return new RunResult(null, response.status(), response.output(), response.errorMessage(),
                response.executionTimeMs() == null ? 0 : response.executionTimeMs(),
                response.memoryUsageKb() == null ? 0 : response.memoryUsageKb());
    }

    private boolean tryAcquireBulkhead() {
        try {
            int waitMs = Math.max(0, properties.getWorkerBulkheadMaxWaitMs());
            return waitMs == 0
                    ? workerBulkhead.tryAcquire()
                    : workerBulkhead.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static RunResult systemError(Throwable exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return new RunResult(exception.getClass().getSimpleName() + ": " + message,
                "SYSTEM_ERROR", "", message, 0, 0);
    }

    private URI workerEndpoint() {
        if (!properties.isWorkerDiscoveryEnabled()) {
            return appendPath(directBaseUri, "internal", "algorithm-judge", "run");
        }
        ServiceInstance instance = loadBalancerClient.choose(properties.getWorkerServiceId());
        if (instance == null) {
            throw new IllegalStateException("未发现可用的判题 Worker: " + properties.getWorkerServiceId());
        }
        URI serviceUri = instance.getUri();
        return UriComponentsBuilder.fromUri(serviceUri)
                .path(normalizePath(properties.getWorkerServicePath()))
                .pathSegment("internal", "algorithm-judge", "run")
                .build()
                .toUri();
    }

    private static URI appendPath(URI baseUri, String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);
        for (String segment : segments) {
            builder.pathSegment(segment);
        }
        return builder.build().toUri();
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        return path.startsWith("/") ? path : "/" + path;
    }

    private static int timeout(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }

    private static String resolveToken(String configured, String tokenFile) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (tokenFile == null || tokenFile.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(tokenFile);
            return Files.isRegularFile(path) ? Files.readString(path).trim() : "";
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    public record RunRequest(String sourceCode, String input, int timeLimitMs, int memoryLimitMb) {}

    public record RunResponse(String status, String output, String errorMessage,
                              Long executionTimeMs, Long memoryUsageKb) {}

    public record RunResult(String systemError, String status, String output, String errorMessage,
                            long executionTimeMs, long memoryUsageKb) {}
}
