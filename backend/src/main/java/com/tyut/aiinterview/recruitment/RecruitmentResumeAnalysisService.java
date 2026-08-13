package com.tyut.aiinterview.recruitment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiGenerationContext;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.CandidateResumeAnalysis;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.freeinterview.ResumeTextExtractor;
import com.tyut.aiinterview.mapper.CandidateResumeAnalysisMapper;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class RecruitmentResumeAnalysisService {
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final int MAX_SKILLS = 20;

    private final CandidateResumeMapper resumeMapper;
    private final CandidateResumeAnalysisMapper analysisMapper;
    private final MediaFileMapper mediaMapper;
    private final LocalObjectStorage storage;
    private final ResumeTextExtractor extractor;
    private final DeepSeekGateway deepSeekGateway;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public RecruitmentResumeAnalysisService(CandidateResumeMapper resumeMapper,
                                             CandidateResumeAnalysisMapper analysisMapper,
                                             MediaFileMapper mediaMapper, LocalObjectStorage storage,
                                             ResumeTextExtractor extractor, DeepSeekGateway deepSeekGateway,
                                             ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.resumeMapper = resumeMapper;
        this.analysisMapper = analysisMapper;
        this.mediaMapper = mediaMapper;
        this.storage = storage;
        this.extractor = extractor;
        this.deepSeekGateway = deepSeekGateway;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public String process(AiTask task) {
        JsonNode input = tree(task.getInputPayload());
        Long resumeId = input.path("resumeId").asLong(0);
        Long analysisId = input.path("analysisId").asLong(0);
        CandidateResume resume = resumeMapper.selectById(resumeId);
        CandidateResumeAnalysis analysis = analysisMapper.selectById(analysisId);
        if (resume == null || analysis == null || !resumeId.equals(analysis.getResumeId())) {
            throw new IllegalStateException("简历解析任务引用不存在");
        }
        if (SUCCESS.equals(analysis.getStatus())) return output(resumeId, analysisId, true);
        markProcessing(resume, analysis);
        try {
            if (resume.getMediaId() == null) throw BusinessException.badRequest("简历没有可解析的文件");
            MediaFile media = mediaMapper.selectById(resume.getMediaId());
            if (media == null || media.getStatus() != MediaFile.AVAILABLE) throw BusinessException.notFound("简历文件不存在或不可用");
            String extractedText;
            try (InputStream inputStream = storage.resource(media.getObjectKey()).getInputStream()) {
                extractedText = extractor.extract(media.getOriginalName(), inputStream);
            } catch (IOException exception) {
                throw new IllegalStateException("读取简历文件失败", exception);
            }
            DeepSeekGateway.Generated<JsonNode> generated = deepSeekGateway.analyzeResume(extractedText, null,
                    new AiGenerationContext(task.getId(), null, null, "RECRUITMENT_RESUME_ANALYSIS", task.getCreatedBy()));
            JsonNode profile = validateProfile(generated.content());
            applySuccess(resume, analysis, extractedText, profile);
            eventPublisher.publishEvent(new ResumeParseCompletedEvent(resumeId, analysis.getAnalysisVersion(), true, null));
            return output(resumeId, analysisId, false);
        } catch (RuntimeException exception) {
            markFailed(resume, analysis, safeError(exception));
            eventPublisher.publishEvent(new ResumeParseCompletedEvent(resumeId, analysis.getAnalysisVersion(), false, safeError(exception)));
            throw exception;
        }
    }

    private void markProcessing(CandidateResume resume, CandidateResumeAnalysis analysis) {
        analysis.setStatus(PROCESSING);
        analysis.setErrorMessage(null);
        analysisMapper.updateById(analysis);
        resume.setParseStatus(PROCESSING);
        resume.setParseError(null);
        resumeMapper.updateById(resume);
    }

    private void applySuccess(CandidateResume resume, CandidateResumeAnalysis analysis, String extractedText, JsonNode profile) {
        String summary = firstText(profile, "candidateProfile", "summary");
        List<String> skills = profile.path("skills").isArray()
                ? new LinkedHashSet<>(toStrings(profile.path("skills"))).stream().limit(MAX_SKILLS).toList()
                : List.of();
        try {
            analysis.setStatus(SUCCESS);
            analysis.setExtractedText(extractedText);
            analysis.setProfileJson(objectMapper.writeValueAsString(profile));
            analysis.setErrorMessage(null);
            analysis.setFinishedAt(LocalDateTime.now());
            analysisMapper.updateById(analysis);
            resume.setSummary(trim(summary, 3000));
            resume.setSkills(objectMapper.writeValueAsString(skills));
            resume.setParseStatus(SUCCESS);
            resume.setParseError(null);
            resume.setParsedAt(LocalDateTime.now());
            resumeMapper.updateById(resume);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存简历解析结果失败", exception);
        }
    }

    private void markFailed(CandidateResume resume, CandidateResumeAnalysis analysis, String error) {
        analysis.setStatus(FAILED);
        analysis.setErrorMessage(error);
        analysis.setFinishedAt(LocalDateTime.now());
        analysisMapper.updateById(analysis);
        resume.setParseStatus(FAILED);
        resume.setParseError(error);
        resumeMapper.updateById(resume);
    }

    private JsonNode validateProfile(JsonNode profile) {
        if (profile == null || !profile.isObject()) throw new IllegalStateException("简历解析结果不是 JSON 对象");
        if (!profile.path("skills").isMissingNode() && !profile.path("skills").isArray()) {
            throw new IllegalStateException("简历解析结果的 skills 字段格式不正确");
        }
        if (firstText(profile, "candidateProfile", "summary").isBlank()) {
            throw new IllegalStateException("简历解析结果缺少候选人画像");
        }
        return profile;
    }

    private List<String> toStrings(JsonNode values) {
        var result = new java.util.ArrayList<String>();
        values.forEach(value -> {
            String item = value.asText("").trim();
            if (!item.isBlank() && item.length() <= 48) result.add(item);
        });
        return result;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String output(Long resumeId, Long analysisId, boolean existing) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("resumeId", resumeId, "analysisId", analysisId, "existing", existing));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化简历解析结果", exception);
        }
    }

    private JsonNode tree(String payload) {
        try {
            return objectMapper.readTree(payload == null ? "{}" : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("简历解析任务参数损坏", exception);
        }
    }

    private String safeError(RuntimeException exception) {
        if (exception instanceof BusinessException) {
            return trim(exception.getMessage(), 300);
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "简历解析失败，请稍后重试";
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("http 401") || normalized.contains("http 403")
                || normalized.contains("api key") || normalized.contains("未找到可用的大模型配置")) {
            return "简历解析服务未正确配置，请联系管理员";
        }
        if (normalized.contains("http 429") || normalized.contains("超时") || normalized.contains("timed out")
                || normalized.contains("请求失败") || normalized.contains("调用 deepseek api")) {
            return "简历解析服务暂时繁忙，请稍后重试";
        }
        return "简历解析失败，请检查文件内容后重试";
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
