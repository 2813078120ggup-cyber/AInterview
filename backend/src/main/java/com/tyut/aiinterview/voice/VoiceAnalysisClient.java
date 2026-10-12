package com.tyut.aiinterview.voice;

import com.tyut.aiinterview.config.VoiceAnalysisProperties;
import com.tyut.aiinterview.voice.VoiceAnalysisDtos.AnalyzeRequest;
import com.tyut.aiinterview.voice.VoiceAnalysisDtos.AnalyzeResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 语音分析服务的 HTTP 客户端。
 *
 * <p>Java 后端向独立 Python 语音分析服务（voice-analysis-service）发送音频分析请求，
 * 返回可追溯的语音维度得分。参考 {@code AlgorithmJudgeWorkerClient} 的连接模式，
 * 但语音分析是旁路增强，失败时降级为「未评估」，不阻塞文本评分主链路。
 */
@Component
public class VoiceAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnalysisClient.class);

    private final RestClient restClient;
    private final VoiceAnalysisProperties properties;
    private final String baseUrl;

    public VoiceAnalysisClient(RestClient.Builder builder, VoiceAnalysisProperties properties) {
        this.properties = properties;
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    /**
     * 分析音频文件。失败时返回 unavailable 结果，不抛出异常（语音为旁路增强）。
     *
     * @param audioPath  音频文件绝对路径
     * @param language   语言代码（zh/en/auto，可为 null）
     * @param transcript 已转写文本（可为 null，用于流利度填充词检测）
     */
    public VoiceAnalysisDtos.VoiceAnalysisResult analyze(String audioPath, String language, String transcript) {
        if (!properties.configured()) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("语音分析服务未启用");
        }
        if (audioPath == null || audioPath.isBlank()) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("未提供音频文件");
        }
        Path path = Path.of(audioPath);
        if (!Files.isRegularFile(path)) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("音频文件不存在: " + audioPath);
        }
        try {
            AnalyzeResponse response = restClient.post()
                    .uri(baseUrl + "/v1/analyze")
                    .body(new AnalyzeRequest(audioPath, language, transcript))
                    .retrieve()
                    .body(AnalyzeResponse.class);
            if (response == null || response.dimensions() == null || response.dimensions().isEmpty()) {
                return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("语音分析服务返回空结果");
            }
            return new VoiceAnalysisDtos.VoiceAnalysisResult(
                    true,
                    response.dimensionScore("speaking_rate"),
                    response.dimensionScore("fluency"),
                    response.dimensionScore("emotion_stability"),
                    response.dimensionScore("clarity"),
                    response.voiceScore(),
                    response.trace() == null ? null : response.trace().durationSec(),
                    buildEvidence(response));
        } catch (RuntimeException exception) {
            log.warn("语音分析服务调用失败（降级为未评估）: {}", exception.getMessage());
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable(exception.getMessage());
        }
    }

    private java.util.Map<String, Object> buildEvidence(AnalyzeResponse response) {
        java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>();
        evidence.put("voice_score", response.voiceScore());
        if (response.dimensions() != null) {
            for (java.util.Map.Entry<String, VoiceAnalysisDtos.DimensionScore> entry : response.dimensions().entrySet()) {
                evidence.put(entry.getKey(), entry.getValue().score());
                evidence.put(entry.getKey() + "_raw", entry.getValue().raw());
            }
        }
        if (response.trace() != null) {
            evidence.put("duration_sec", response.trace().durationSec());
            evidence.put("sample_rate", response.trace().sampleRate());
            evidence.put("rules", response.trace().rules());
            evidence.put("warnings", response.trace().warnings());
        }
        return evidence;
    }
}
