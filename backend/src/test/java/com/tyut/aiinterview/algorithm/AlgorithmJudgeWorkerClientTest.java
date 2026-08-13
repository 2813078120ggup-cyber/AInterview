package com.tyut.aiinterview.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.client.RestClient;

class AlgorithmJudgeWorkerClientTest {
    private HttpServer server;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> requestToken;
    private volatile CountDownLatch requestStarted;
    private volatile CountDownLatch releaseResponse;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        requestPath = new AtomicReference<>();
        requestToken = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            CountDownLatch started = requestStarted;
            CountDownLatch release = releaseResponse;
            if (started != null && release != null) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "{\"status\":\"ACCEPTED\",\"output\":\"42\",\"errorMessage\":null,\"executionTimeMs\":3,\"memoryUsageKb\":1024}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void choosesWorkerFromEurekaServiceId() {
        AlgorithmJudgeProperties properties = properties();
        properties.setWorkerDiscoveryEnabled(true);
        properties.setWorkerServicePath("/api");
        properties.setInternalToken("test-token");
        LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);
        when(loadBalancerClient.choose("algorithm-judge-worker"))
                .thenReturn(new DefaultServiceInstance("worker-1", "algorithm-judge-worker",
                        "127.0.0.1", server.getAddress().getPort(), false));

        AlgorithmJudgeWorkerClient.RunResult result = new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, loadBalancerClient)
                .run("public class Main {}", "42", 1000, 128);

        assertEquals("ACCEPTED", result.status());
        assertEquals("42", result.output());
        assertEquals("/api/internal/algorithm-judge/run", requestPath.get());
        assertEquals("test-token", requestToken.get());
        verify(loadBalancerClient).choose("algorithm-judge-worker");
    }

    @Test
    void usesDirectBaseUrlWhenDiscoveryIsDisabled() {
        AlgorithmJudgeProperties properties = properties();
        properties.setWorkerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);

        AlgorithmJudgeWorkerClient.RunResult result = new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, loadBalancerClient)
                .run("public class Main {}", "", 1000, 128);

        assertEquals("ACCEPTED", result.status());
        assertEquals("/api/internal/algorithm-judge/run", requestPath.get());
        verify(loadBalancerClient, never()).choose("algorithm-judge-worker");
    }

    @Test
    void failsFastWhenDiscoveryHasNoWorkerInstance() {
        AlgorithmJudgeProperties properties = properties();
        properties.setWorkerDiscoveryEnabled(true);
        LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);
        when(loadBalancerClient.choose("algorithm-judge-worker")).thenReturn(null);

        AlgorithmJudgeWorkerClient.RunResult result = new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, loadBalancerClient)
                .run("public class Main {}", "", 1000, 128);

        assertEquals("SYSTEM_ERROR", result.status());
        assertEquals("IllegalStateException: 未发现可用的判题 Worker: algorithm-judge-worker", result.systemError());
    }

    @Test
    void rejectsRequiredTokenWhenConfigurationIsEmpty() {
        AlgorithmJudgeProperties properties = properties();
        properties.setRequireInternalToken(true);
        properties.setInternalTokenFile(tempDir.resolve("missing-token").toString());

        assertThrows(IllegalStateException.class, () -> new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, mock(LoadBalancerClient.class)));
    }

    @Test
    void readsRequiredTokenFromSecretFile() throws IOException {
        Path tokenFile = tempDir.resolve("algorithm-judge-internal-token");
        Files.writeString(tokenFile, "file-token\n", StandardCharsets.UTF_8);
        AlgorithmJudgeProperties properties = properties();
        properties.setWorkerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        properties.setRequireInternalToken(true);
        properties.setInternalTokenFile(tokenFile.toString());

        AlgorithmJudgeWorkerClient.RunResult result = new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, mock(LoadBalancerClient.class))
                .run("public class Main {}", "", 1000, 128);

        assertEquals("ACCEPTED", result.status());
        assertEquals("file-token", requestToken.get());
    }

    @Test
    void opensCircuitAfterConfiguredFailures() {
        AlgorithmJudgeProperties properties = properties();
        properties.setWorkerDiscoveryEnabled(true);
        properties.setWorkerCircuitBreakerName("algorithmJudgeWorkerTest");
        LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);
        when(loadBalancerClient.choose("algorithm-judge-worker")).thenReturn(null);
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        AlgorithmJudgeWorkerClient client = new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, loadBalancerClient, registry);

        assertEquals("SYSTEM_ERROR", client.run("public class Main {}", "", 1000, 128).status());
        assertEquals("SYSTEM_ERROR", client.run("public class Main {}", "", 1000, 128).status());
        AlgorithmJudgeWorkerClient.RunResult rejected = client.run(
                "public class Main {}", "", 1000, 128);

        assertEquals("SYSTEM_ERROR", rejected.status());
        assertTrue(rejected.systemError().contains("CallNotPermittedException"));
        assertEquals(CircuitBreaker.State.OPEN,
                registry.circuitBreaker("algorithmJudgeWorkerTest").getState());
        verify(loadBalancerClient, times(2)).choose("algorithm-judge-worker");
    }

    @Test
    void rejectsConcurrentCallWhenBulkheadIsFull() throws Exception {
        AlgorithmJudgeProperties properties = properties();
        properties.setWorkerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        properties.setWorkerBulkheadMaxConcurrent(1);
        properties.setWorkerBulkheadMaxWaitMs(0);
        AlgorithmJudgeWorkerClient client = new AlgorithmJudgeWorkerClient(
                RestClient.builder(), properties, mock(LoadBalancerClient.class));
        requestStarted = new CountDownLatch(1);
        releaseResponse = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AlgorithmJudgeWorkerClient.RunResult> first = executor.submit(
                    () -> client.run("public class Main {}", "", 1000, 128));
            assertTrue(requestStarted.await(2, TimeUnit.SECONDS));

            AlgorithmJudgeWorkerClient.RunResult rejected = client.run(
                    "public class Main {}", "", 1000, 128);

            assertEquals("SYSTEM_ERROR", rejected.status());
            assertTrue(rejected.systemError().contains("并发隔离已满"));
            releaseResponse.countDown();
            assertEquals("ACCEPTED", first.get(3, TimeUnit.SECONDS).status());
        } finally {
            releaseResponse.countDown();
            executor.shutdownNow();
        }
    }

    private AlgorithmJudgeProperties properties() {
        AlgorithmJudgeProperties properties = new AlgorithmJudgeProperties();
        properties.setWorkerConnectTimeoutMs(1000);
        properties.setWorkerReadTimeoutMs(1000);
        return properties;
    }
}
