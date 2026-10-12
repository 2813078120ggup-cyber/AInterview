package com.tyut.aiinterview.voice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.config.VoiceAnalysisProperties;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewRecordingSegment;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.mapper.InterviewRecordingSegmentMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 语音分析业务层：解析回答对应题目的录音段，调用语音分析服务，
 * 返回可追溯的语音维度结果，供评分引擎融合。
 *
 * <p>音频来源（方案 B）：直接从 {@code interview_recording_segment} 表按题目聚合录音段，
 * 不依赖 {@code interview_answer.media_id / audio_url}。一次回答可能被切成多个录音段，
 * 本类逐段分析后按时长加权融合，单段时退化为该段结果。
 */
@Service
public class VoiceAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnalysisService.class);

    private final VoiceAnalysisClient client;
    private final VoiceAnalysisProperties properties;
    private final InterviewRecordingSegmentMapper segmentMapper;
    private final MediaFileMapper mediaMapper;
    private final LocalObjectStorage storage;

    public VoiceAnalysisService(VoiceAnalysisClient client,
                                VoiceAnalysisProperties properties,
                                InterviewRecordingSegmentMapper segmentMapper,
                                MediaFileMapper mediaMapper,
                                LocalObjectStorage storage) {
        this.client = client;
        this.properties = properties;
        this.segmentMapper = segmentMapper;
        this.mediaMapper = mediaMapper;
        this.storage = storage;
    }

    /**
     * 分析某条回答对应题目的语音表现。
     *
     * <p>按回答的 {@code interviewQuestionId} 查询该题的录音段，逐段解析本地音频路径，
     * 交由语音分析服务评分后按时长加权融合。若该题无录音段，返回「未评估」，不影响文本评分。
     */
    public VoiceAnalysisDtos.VoiceAnalysisResult analyzeAnswer(InterviewAnswer answer) {
        if (!properties.configured()) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("语音分析服务未启用");
        }
        if (answer == null || answer.getInterviewQuestionId() == null) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("回答为空");
        }
        List<String> audioPaths = resolveAudioPaths(answer.getInterviewQuestionId());
        if (audioPaths.isEmpty()) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("该回答无音频录制，语音维度未评估");
        }
        return analyzeSegments(audioPaths, transcript(answer));
    }

    /**
     * 语音分析所需的转写文本：优先使用 ASR 转写 {@code transcript}，
     * 为空时降级使用作答文本 {@code answerContent}（用于流利度填充词检测）。
     */
    private String transcript(InterviewAnswer answer) {
        String transcript = answer.getTranscript();
        if (transcript != null && !transcript.isBlank()) return transcript;
        String content = answer.getAnswerContent();
        if (content != null && !content.isBlank()) return content;
        return answer.getAnswerData();
    }

    /**
     * 解析某题所有录音段对应的本地音频路径（按 segmentNo 升序）。
     */
    private List<String> resolveAudioPaths(Long interviewQuestionId) {
        List<InterviewRecordingSegment> segments = segmentMapper.selectList(
                new LambdaQueryWrapper<InterviewRecordingSegment>()
                        .eq(InterviewRecordingSegment::getInterviewQuestionId, interviewQuestionId)
                        .orderByAsc(InterviewRecordingSegment::getSegmentNo));
        List<String> paths = new ArrayList<>();
        for (InterviewRecordingSegment segment : segments) {
            if (segment.getMediaId() == null) continue;
            MediaFile media = mediaMapper.selectById(segment.getMediaId());
            if (media == null || media.getStatus() == null || media.getStatus() != MediaFile.AVAILABLE) continue;
            try {
                paths.add(storage.path(media.getObjectKey()).toString());
            } catch (RuntimeException exception) {
                log.warn("解析录音段音频路径失败 segmentId={} mediaId={}: {}",
                        segment.getId(), segment.getMediaId(), exception.getMessage());
            }
        }
        return paths;
    }

    /**
     * 逐段分析并按时长加权融合语音维度得分。
     *
     * <p>每段返回 voiceScore + 四维子分 + 时长；以时长占比为权重求各分加权平均。
     * 全部段分析失败时返回「未评估」。
     */
    private VoiceAnalysisDtos.VoiceAnalysisResult analyzeSegments(List<String> audioPaths, String transcript) {
        List<VoiceAnalysisDtos.VoiceAnalysisResult> results = new ArrayList<>();
        List<Double> durations = new ArrayList<>();
        for (String audioPath : audioPaths) {
            VoiceAnalysisDtos.VoiceAnalysisResult result = client.analyze(audioPath, "zh", transcript);
            if (!result.available() || result.voiceScore() == null) continue;
            results.add(result);
            durations.add(result.durationSec() == null ? 0.0 : result.durationSec());
        }
        if (results.isEmpty()) {
            return VoiceAnalysisDtos.VoiceAnalysisResult.unavailable("语音分析服务未返回有效结果");
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        double totalDuration = durations.stream().mapToDouble(Double::doubleValue).sum();
        if (totalDuration <= 0) {
            // 无时长信息时退化为简单平均
            return average(results, null);
        }
        return weightedAverage(results, durations, totalDuration);
    }

    private VoiceAnalysisDtos.VoiceAnalysisResult average(List<VoiceAnalysisDtos.VoiceAnalysisResult> results,
                                                          List<Double> weights) {
        double total = weights == null ? results.size() : weights.stream().mapToDouble(Double::doubleValue).sum();
        return merge(results, weights, total);
    }

    private VoiceAnalysisDtos.VoiceAnalysisResult weightedAverage(List<VoiceAnalysisDtos.VoiceAnalysisResult> results,
                                                                  List<Double> weights, double total) {
        return merge(results, weights, total);
    }

    private VoiceAnalysisDtos.VoiceAnalysisResult merge(List<VoiceAnalysisDtos.VoiceAnalysisResult> results,
                                                        List<Double> weights, double total) {
        if (total <= 0) total = results.size();
        Double speakingRate = weightedMean(results, weights, total, VoiceAnalysisDtos.VoiceAnalysisResult::speakingRate);
        Double fluency = weightedMean(results, weights, total, VoiceAnalysisDtos.VoiceAnalysisResult::fluency);
        Double emotionStability = weightedMean(results, weights, total, VoiceAnalysisDtos.VoiceAnalysisResult::emotionStability);
        Double clarity = weightedMean(results, weights, total, VoiceAnalysisDtos.VoiceAnalysisResult::clarity);
        Double voiceScore = weightedMean(results, weights, total, VoiceAnalysisDtos.VoiceAnalysisResult::voiceScore);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("voice_score", voiceScore);
        evidence.put("speaking_rate", speakingRate);
        evidence.put("fluency", fluency);
        evidence.put("emotion_stability", emotionStability);
        evidence.put("clarity", clarity);
        evidence.put("segment_count", results.size());
        List<Object> segments = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("duration_sec", results.get(i).durationSec());
            if (weights != null && i < weights.size()) seg.put("weight", weights.get(i));
            if (results.get(i).evidence() != null) seg.put("evidence", results.get(i).evidence());
            segments.add(seg);
        }
        evidence.put("segments", segments);

        Double totalDuration = results.stream()
                .map(VoiceAnalysisDtos.VoiceAnalysisResult::durationSec)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return new VoiceAnalysisDtos.VoiceAnalysisResult(true, speakingRate, fluency, emotionStability, clarity,
                voiceScore, totalDuration, evidence);
    }

    private Double weightedMean(List<VoiceAnalysisDtos.VoiceAnalysisResult> results, List<Double> weights, double total,
                                java.util.function.Function<VoiceAnalysisDtos.VoiceAnalysisResult, Double> getter) {
        double sum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < results.size(); i++) {
            Double value = getter.apply(results.get(i));
            if (value == null) continue;
            double w = weights == null ? 1.0 : (i < weights.size() ? weights.get(i) : 0.0);
            sum += value * w;
            weightSum += w;
        }
        if (weightSum <= 0) return null;
        return round2(sum / weightSum);
    }

    private Double round2(double value) {
        return Double.valueOf(BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
    }

    /**
     * 将语音维度得分融入综合分。当前文本四维评分为主，语音维度按可配置权重叠加，
     * 未评估（available=false）时保持原分不变。
     *
     * @param baseScore      文本综合分（0-100）
     * @param voice          语音分析结果
     * @param voiceWeight    语音维度权重（0~1）
     */
    public java.math.BigDecimal fuseVoiceScore(java.math.BigDecimal baseScore,
                                               VoiceAnalysisDtos.VoiceAnalysisResult voice,
                                               double voiceWeight) {
        if (voice == null || !voice.available() || voice.voiceScore() == null) {
            return baseScore;
        }
        if (baseScore == null) {
            return java.math.BigDecimal.valueOf(voice.voiceScore());
        }
        double w = Math.max(0.0, Math.min(1.0, voiceWeight));
        double fused = baseScore.doubleValue() * (1.0 - w) + voice.voiceScore() * w;
        return java.math.BigDecimal.valueOf(fused)
                .setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
