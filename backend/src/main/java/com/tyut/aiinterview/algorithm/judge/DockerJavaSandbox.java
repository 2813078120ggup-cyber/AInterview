package com.tyut.aiinterview.algorithm.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithm.judge.archive.JudgeInputArchiveWriter;
import com.tyut.aiinterview.algorithm.judge.archive.JudgeResultArchiveReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Docker Java 17 判题沙箱。
 *
 * <p>链路：create（不启动）→ copyArchiveToContainer 写入输入 tar → start
 * → waitContainer 等待主进程（镜像 ENTRYPOINT 内完成编译 + 全部用例）
 * → copyArchiveFromContainer 取回结果 tar → 解析 → 强制删除容器。
 *
 * <p>全程不使用 docker exec。
 */
@Component
public class DockerJavaSandbox {
    private static final Logger log = LoggerFactory.getLogger(DockerJavaSandbox.class);

    private final AlgorithmJudgeProperties properties;
    private volatile DockerClient dockerClient;

    public DockerJavaSandbox(AlgorithmJudgeProperties properties) {
        this.properties = properties;
    }

    public record CaseResult(String status, String stdout, String stderr, long timeMs, long memoryKb) {}

    public record SandboxResult(String systemError, String status, String compileMessage,
                                int passedCount, int totalCount, long maxTimeMs, long maxMemoryKb,
                                List<CaseResult> cases) {
        public static SandboxResult systemError(String message) {
            return new SandboxResult(message, "SYSTEM_ERROR", null, 0, 0, 0, 0, List.of());
        }
    }

    public SandboxResult execute(String sourceCode, List<JudgeInputArchiveWriter.TestCaseData> testCases,
                                 int timeLimitMs, int memoryLimitMb, boolean compareOutput) {
        String containerId = null;
        Path inputArchive = null;
        try {
            int javaXmxMb = calculateJavaXmx(memoryLimitMb);
            int outputLimitKb = (int) Math.max(1, properties.getOutputLimitBytes() / 1024);
            inputArchive = JudgeInputArchiveWriter.create(
                    sourceCode, testCases, properties.getCompileTimeoutSeconds(),
                    timeLimitMs, javaXmxMb, outputLimitKb, compareOutput);

            CreateContainerResponse container = createContainer(memoryLimitMb);
            containerId = container.getId();

            try (InputStream archiveInput = Files.newInputStream(inputArchive)) {
                dockerClient().copyArchiveToContainerCmd(containerId)
                        .withRemotePath("/workspace")
                        .withTarInputStream(archiveInput)
                        .exec();
            }

            dockerClient().startContainerCmd(containerId).exec();

            Integer exitCode = waitForContainer(containerId, totalTimeout(timeLimitMs, testCases.size()));
            if (exitCode == null) {
                killQuietly(containerId);
                return SandboxResult.systemError("判题容器等待超时");
            }

            Map<String, byte[]> resultFiles;
            try (InputStream resultArchive = dockerClient()
                    .copyArchiveFromContainerCmd(containerId, "/workspace/output")
                    .exec()) {
                resultFiles = JudgeResultArchiveReader.read(resultArchive);
            }
            return parseResult(resultFiles, exitCode);
        } catch (Exception exception) {
            log.error("judge sandbox execution failed", exception);
            if (containerId != null) killQuietly(containerId);
            return SandboxResult.systemError(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } finally {
            if (containerId != null) removeQuietly(containerId);
            if (inputArchive != null) {
                try {
                    Files.deleteIfExists(inputArchive);
                } catch (IOException ignored) {
                    // 交由临时文件清理
                }
            }
        }
    }

    private SandboxResult parseResult(Map<String, byte[]> files, int containerExitCode) {
        Map<String, byte[]> normalized = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        byte[] summaryBytes = normalized.get("summary.properties");
        if (summaryBytes == null) {
            return SandboxResult.systemError("判题结果缺失 summary.properties（容器退出码 " + containerExitCode + "）");
        }
        Properties summary = new Properties();
        try (InputStream input = new ByteArrayInputStream(summaryBytes)) {
            summary.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return SandboxResult.systemError("解析判题摘要失败: " + exception.getMessage());
        }
        String status = summary.getProperty("status", "SYSTEM_ERROR");
        if ("COMPILE_TIMEOUT".equals(status)) {
            status = "COMPILE_ERROR";
        }
        int passed = parseInt(summary.getProperty("passedCount", "0"));
        int total = parseInt(summary.getProperty("totalCount", "0"));
        long maxTime = parseLong(summary.getProperty("maxTimeMs", "0"));
        long maxMemory = parseLong(summary.getProperty("maxMemoryKb", "0"));
        String compileMessage = null;
        byte[] compileErr = normalized.get("compile.stderr");
        byte[] compileOut = normalized.get("compile.stdout");
        if (compileErr != null || compileOut != null) {
            compileMessage = (compileErr == null ? "" : new String(compileErr, StandardCharsets.UTF_8))
                    + (compileOut == null ? "" : new String(compileOut, StandardCharsets.UTF_8));
        }
        List<CaseResult> cases = new ArrayList<>();
        for (int index = 1; index <= Math.max(total, 1); index++) {
            String prefix = String.format("cases/%04d/", index);
            byte[] caseStatus = normalized.get(prefix + "status");
            if (caseStatus == null) break;
            cases.add(new CaseResult(
                    new String(caseStatus, StandardCharsets.UTF_8).trim(),
                    text(normalized, prefix + "stdout"),
                    text(normalized, prefix + "stderr"),
                    parseLong(text(normalized, prefix + "time_ms")),
                    parseLong(text(normalized, prefix + "memory_kb"))));
        }
        return new SandboxResult(null, status, compileMessage, passed, total, maxTime, maxMemory, cases);
    }

    private static String normalizeKey(String name) {
        String key = name.replace('\\', '/');
        while (key.startsWith("./")) {
            key = key.substring(2);
        }
        if (key.startsWith("workspace/output/")) {
            key = key.substring("workspace/output/".length());
        } else if (key.startsWith("output/")) {
            key = key.substring("output/".length());
        }
        return key;
    }

    private static String text(Map<String, byte[]> files, String name) {
        byte[] bytes = files.get(name);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "0" : value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private CreateContainerResponse createContainer(int memoryLimitMb) {
        long memoryBytes = memoryLimitMb * 1024L * 1024L;
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withNanoCPUs(500_000_000L)
                .withPidsLimit(64L)
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges:true"));
        return dockerClient().createContainerCmd(properties.getRunnerImage())
                .withName("algorithm-judge-" + UUID.randomUUID().toString().replace("-", ""))
                .withHostConfig(hostConfig)
                .withUser("10001:10001")
                .withWorkingDir("/workspace")
                .withAttachStdout(false)
                .withAttachStderr(false)
                .withTty(false)
                .exec();
    }

    private Integer waitForContainer(String containerId, Duration timeout)
            throws InterruptedException, IOException {
        try (WaitContainerResultCallback callback = new WaitContainerResultCallback()) {
            return dockerClient().waitContainerCmd(containerId)
                    .exec(callback)
                    .awaitStatusCode(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private Duration totalTimeout(int timeLimitMs, int caseCount) {
        long compileTimeoutMs = properties.getCompileTimeoutSeconds() * 1000L;
        long executionTimeoutMs = (long) timeLimitMs * Math.max(caseCount, 1);
        long overheadMs = 5_000L + 1_000L * Math.max(caseCount, 1);
        return Duration.ofMillis(compileTimeoutMs + executionTimeoutMs + overheadMs);
    }

    private static int calculateJavaXmx(int containerMemoryMb) {
        return Math.max(32, Math.min(containerMemoryMb - 32, containerMemoryMb * 3 / 4));
    }

    private void killQuietly(String containerId) {
        try {
            dockerClient().killContainerCmd(containerId).exec();
        } catch (Exception ignored) {
            // 容器可能已经停止
        }
    }

    private void removeQuietly(String containerId) {
        try {
            dockerClient().removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
        } catch (Exception ignored) {
            // 定时清理任务处理残留容器
        }
    }

    private DockerClient dockerClient() {
        DockerClient current = dockerClient;
        if (current == null) {
            synchronized (this) {
                if (dockerClient == null) {
                    String host = properties.getDockerHost();
                    if (host == null || host.isBlank()) {
                        host = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                                ? "npipe:////./pipe/docker_engine"
                                : "unix:///var/run/docker.sock";
                    }
                    DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                            .withDockerHost(host)
                            .build();
                    DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                            .dockerHost(config.getDockerHost())
                            .sslConfig(config.getSSLConfig())
                            .build();
                    dockerClient = DockerClientImpl.getInstance(config, httpClient);
                }
                current = dockerClient;
            }
        }
        return current;
    }
}
