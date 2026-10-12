package com.tyut.aiinterview.voice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 语音分析服务的请求/响应 DTO。
 */
public final class VoiceAnalysisDtos {
    private VoiceAnalysisDtos() {
    }

    /**
     * 发送给语音分析服务的请求体。
     */
    public record AnalyzeRequest(
            @JsonProperty("audio_path") String audioPath,
            String language,
            String transcript) {
    }

    /**
     * 单维度得分与原始证据。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DimensionScore(
            double score,
            Object raw,
            String unit) {
    }

    /**
     * 可追溯信息。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraceInfo(
            @JsonProperty("audio_path") String audioPath,
            @JsonProperty("sample_rate") int sampleRate,
            @JsonProperty("duration_sec") double durationSec,
            Map<String, Object> rules,
            List<String> warnings) {
    }

    /**
     * 语音分析响应。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalyzeResponse(
            String status,
            Map<String, DimensionScore> dimensions,
            @JsonProperty("voice_score") double voiceScore,
            TraceInfo trace,
            String message) {

        public Double dimensionScore(String key) {
            if (dimensions == null) return null;
            DimensionScore dim = dimensions.get(key);
            return dim == null ? null : dim.score();
        }
    }

    /**
     * Java 侧统一封装的语音分析结果。
     */
    public record VoiceAnalysisResult(
            boolean available,
            Double speakingRate,
            Double fluency,
            Double emotionStability,
            Double clarity,
            Double voiceScore,
            Double durationSec,
            Map<String, Object> evidence) {

        public static VoiceAnalysisResult unavailable(String reason) {
            return new VoiceAnalysisResult(false, null, null, null, null, null, null,
                    Map.of("unavailable_reason", reason == null ? "语音分析服务不可用" : reason));
        }
    }
}
