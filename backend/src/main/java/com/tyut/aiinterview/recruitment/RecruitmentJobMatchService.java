package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tyut.aiinterview.ai.AiGenerationContext;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.CandidateResumeAnalysis;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobMatchEvaluation;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.mapper.CandidateResumeAnalysisMapper;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobMatchEvaluationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecruitmentJobMatchService {
    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final int MAX_DETAIL_ITEMS = 8;

    private final JobApplicationMapper applicationMapper;
    private final JobPositionMapper positionMapper;
    private final CandidateResumeMapper resumeMapper;
    private final CandidateResumeAnalysisMapper analysisMapper;
    private final JobMatchEvaluationMapper evaluationMapper;
    private final DeepSeekGateway deepSeekGateway;
    private final ObjectMapper objectMapper;

    public RecruitmentJobMatchService(JobApplicationMapper applicationMapper, JobPositionMapper positionMapper,
                                      CandidateResumeMapper resumeMapper, CandidateResumeAnalysisMapper analysisMapper,
                                      JobMatchEvaluationMapper evaluationMapper, DeepSeekGateway deepSeekGateway,
                                      ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.positionMapper = positionMapper;
        this.resumeMapper = resumeMapper;
        this.analysisMapper = analysisMapper;
        this.evaluationMapper = evaluationMapper;
        this.deepSeekGateway = deepSeekGateway;
        this.objectMapper = objectMapper;
    }

    public String process(AiTask task) {
        JsonNode input = tree(task.getInputPayload());
        Long applicationId = input.path("applicationId").asLong(0);
        int requestedVersion = input.path("resumeVersion").asInt(0);
        int requestedEvaluationVersion = input.path("evaluationVersion").asInt(1);
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null) throw new IllegalStateException("岗位匹配任务引用的申请不存在");
        CandidateResume resume = application.getResumeId() == null ? null : resumeMapper.selectById(application.getResumeId());
        JobPosition position = positionMapper.selectById(application.getPositionId());
        if (resume == null || position == null) throw new IllegalStateException("岗位匹配任务缺少岗位或简历");
        int currentVersion = version(resume);
        int currentEvaluationVersion = Math.max(1, value(application.getMatchEvaluationVersion()));
        if (requestedVersion != currentVersion || requestedEvaluationVersion != currentEvaluationVersion) {
            return output(applicationId, currentVersion, currentEvaluationVersion, null, true);
        }
        if (SUCCESS.equals(application.getMatchStatus()) && currentVersion == value(application.getMatchVersion())
                && currentEvaluationVersion == value(application.getMatchEvaluationVersion())) {
            return output(applicationId, currentVersion, currentEvaluationVersion, application.getMatchScore(), false);
        }
        markProcessing(application, currentVersion, currentEvaluationVersion);
        try {
            if (!isReady(resume)) throw new IllegalStateException("简历尚未完成解析，岗位匹配暂不可执行");
            CandidateResumeAnalysis analysis = latestSuccess(resume.getId(), currentVersion);
            String profile = analysis == null ? blankToDefault(resume.getSummary(), "未形成结构化画像") : analysis.getProfileJson();
            String resumeText = analysis == null ? resumeFacts(resume) : analysis.getExtractedText();
            JobMatchEvaluation evaluation = startEvaluation(application, position, resume, analysis,
                    currentVersion, currentEvaluationVersion, task.getId(), profile);
            RuleScore ruleScore = calculateRuleScore(position, resume);
            DeepSeekGateway.Generated<JsonNode> generated = deepSeekGateway.matchResumeToJob(
                    position.getName(), position.getDescription(), position.getRequirements(), position.getSkillTags(),
                    profile, resume.getSkills(), resumeText,
                    new AiGenerationContext(task.getId(), null, null, "RECRUITMENT_JOB_MATCH", task.getCreatedBy()));
            JsonNode result = validate(generated.content());
            applySuccess(application, evaluation, currentVersion, currentEvaluationVersion, ruleScore, result, generated);
            return output(applicationId, currentVersion, currentEvaluationVersion, application.getMatchScore(), false);
        } catch (RuntimeException exception) {
            markFailed(application, currentEvaluationVersion, safeError(exception));
            throw exception;
        }
    }

    private void markProcessing(JobApplication application, int version, int evaluationVersion) {
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, application.getId())
                .eq(JobApplication::getMatchVersion, version)
                .eq(JobApplication::getMatchEvaluationVersion, evaluationVersion)
                .set(JobApplication::getMatchStatus, PROCESSING)
                .set(JobApplication::getMatchError, null)
                .set(JobApplication::getMatchStartedAt, LocalDateTime.now())
                .set(JobApplication::getMatchCompletedAt, null));
        if (updated == 0) throw new IllegalStateException("岗位匹配任务已过期，请重新发起匹配");
        application.setMatchStatus(PROCESSING);
        application.setMatchVersion(version);
        application.setMatchEvaluationVersion(evaluationVersion);
        application.setMatchError(null);
        application.setMatchStartedAt(LocalDateTime.now());
        application.setMatchCompletedAt(null);
    }

    private void applySuccess(JobApplication application, JobMatchEvaluation evaluation, int resumeVersion,
                              int evaluationVersion, RuleScore ruleScore, JsonNode result,
                              DeepSeekGateway.Generated<JsonNode> generated) {
        BigDecimal aiScore = BigDecimal.valueOf(result.path("score").asInt()).setScale(2);
        BigDecimal finalScore = ruleScore.score().multiply(BigDecimal.valueOf(0.60))
                .add(aiScore.multiply(BigDecimal.valueOf(0.40))).setScale(2, RoundingMode.HALF_UP);
        application.setMatchScore(finalScore);
        application.setMatchSummary(trim(result.path("summary").asText().trim(), 1000));
        application.setMatchDetails(write(normalizeDetails(result, ruleScore, aiScore, finalScore)));
        application.setMatchStatus(SUCCESS);
        application.setMatchVersion(resumeVersion);
        application.setMatchEvaluationVersion(evaluationVersion);
        application.setMatchError(null);
        application.setMatchCompletedAt(LocalDateTime.now());
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, application.getId())
                .eq(JobApplication::getMatchVersion, resumeVersion)
                .eq(JobApplication::getMatchEvaluationVersion, evaluationVersion)
                .set(JobApplication::getMatchScore, finalScore)
                .set(JobApplication::getMatchSummary, application.getMatchSummary())
                .set(JobApplication::getMatchDetails, application.getMatchDetails())
                .set(JobApplication::getMatchStatus, SUCCESS)
                .set(JobApplication::getMatchError, null)
                .set(JobApplication::getMatchCompletedAt, LocalDateTime.now()));
        if (updated == 0) throw new IllegalStateException("岗位匹配结果已过期，请保留最新版本");
        if (evaluation != null) {
            evaluation.setStatus(SUCCESS);
            evaluation.setRuleScore(ruleScore.score());
            evaluation.setAiScore(aiScore);
            evaluation.setFinalScore(finalScore);
            evaluation.setSummary(application.getMatchSummary());
            evaluation.setRuleMatchedSkills(write(ruleScore.matchedSkills()));
            evaluation.setMatchedSkills(write(normalizeList(result.path("matchedSkills"))));
            evaluation.setStrengths(write(normalizeList(result.path("strengths"))));
            evaluation.setGaps(write(normalizeList(result.path("gaps"))));
            evaluation.setRisks(write(normalizeList(result.path("risks"))));
            evaluation.setEvidence(write(normalizeList(result.path("evidence"))));
            evaluation.setRecommendation(trim(result.path("recommendation").asText("建议人工复核"), 80));
            evaluation.setConfidence(confidence(result.path("confidence").asText("MEDIUM")));
            evaluation.setProviderName(blankToDefault(generated.providerCode(), "deepseek"));
            evaluation.setModelName(generated.model());
            evaluation.setPromptVersion(generated.promptVersion());
            evaluation.setErrorMessage(null);
            evaluation.setFinishedAt(LocalDateTime.now());
            evaluationMapper.updateById(evaluation);
        }
    }

    private void markFailed(JobApplication application, int evaluationVersion, String error) {
        application.setMatchStatus(FAILED);
        application.setMatchError(trim(error, 1000));
        application.setMatchCompletedAt(LocalDateTime.now());
        applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, application.getId())
                .eq(JobApplication::getMatchEvaluationVersion, evaluationVersion)
                .set(JobApplication::getMatchStatus, FAILED)
                .set(JobApplication::getMatchError, trim(error, 1000))
                .set(JobApplication::getMatchCompletedAt, LocalDateTime.now()));
        if (evaluationMapper != null) {
            JobMatchEvaluation evaluation = evaluationMapper.selectOne(new LambdaQueryWrapper<JobMatchEvaluation>()
                    .eq(JobMatchEvaluation::getApplicationId, application.getId())
                    .eq(JobMatchEvaluation::getEvaluationVersion, evaluationVersion)
                    .last("LIMIT 1"));
            if (evaluation != null) {
                evaluation.setStatus(FAILED);
                evaluation.setErrorMessage(trim(error, 1000));
                evaluation.setFinishedAt(LocalDateTime.now());
                evaluationMapper.updateById(evaluation);
            }
        }
    }

    private JsonNode validate(JsonNode result) {
        if (result == null || !result.isObject()) throw new IllegalStateException("岗位匹配结果不是 JSON 对象");
        int score = result.path("score").asInt(-1);
        if (score < 0 || score > 100) throw new IllegalStateException("岗位匹配结果的 score 必须在 0 到 100 之间");
        if (!StringUtils.hasText(result.path("summary").asText())) throw new IllegalStateException("岗位匹配结果缺少 summary");
        return result;
    }

    private ObjectNode normalizeDetails(JsonNode result, RuleScore ruleScore, BigDecimal aiScore, BigDecimal finalScore) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("score", finalScore);
        details.put("ruleScore", ruleScore.score());
        details.put("aiScore", aiScore);
        details.put("summary", trim(result.path("summary").asText(), 1000));
        ArrayNode ruleMatchedSkills = details.putArray("ruleMatchedSkills");
        ruleScore.matchedSkills().forEach(ruleMatchedSkills::add);
        copyList(details, result, "matchedSkills");
        copyList(details, result, "strengths");
        copyList(details, result, "gaps");
        copyList(details, result, "risks");
        copyList(details, result, "evidence");
        details.put("recommendation", trim(result.path("recommendation").asText("建议人工复核"), 80));
        details.put("notice", "AI 匹配仅用于辅助筛选，最终结论需结合面试与人工审核。");
        return details;
    }

    private JobMatchEvaluation startEvaluation(JobApplication application, JobPosition position, CandidateResume resume,
                                               CandidateResumeAnalysis analysis, int resumeVersion,
                                               int evaluationVersion, Long taskId, String profile) {
        if (evaluationMapper == null) return null;
        JobMatchEvaluation evaluation = evaluationMapper.selectOne(new LambdaQueryWrapper<JobMatchEvaluation>()
                .eq(JobMatchEvaluation::getApplicationId, application.getId())
                .eq(JobMatchEvaluation::getEvaluationVersion, evaluationVersion)
                .last("LIMIT 1"));
        if (evaluation == null) {
            evaluation = new JobMatchEvaluation();
            evaluation.setApplicationId(application.getId());
            evaluation.setEvaluationVersion(evaluationVersion);
            evaluation.setCreatedAt(LocalDateTime.now());
            evaluation.setPositionSnapshot(positionSnapshot(position));
            evaluation.setResumeSnapshot(resumeSnapshot(resume, analysis, profile));
            evaluationMapper.insert(evaluation);
        }
        evaluation.setAnalysisId(analysis == null ? null : analysis.getId());
        evaluation.setAiTaskId(taskId);
        evaluation.setResumeVersion(resumeVersion);
        evaluation.setStatus(PROCESSING);
        evaluation.setErrorMessage(null);
        evaluationMapper.updateById(evaluation);
        return evaluation;
    }

    private String positionSnapshot(JobPosition position) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("id", position.getId());
        snapshot.put("name", blankToDefault(position.getName(), ""));
        snapshot.put("description", blankToDefault(position.getDescription(), ""));
        snapshot.put("requirements", blankToDefault(position.getRequirements(), ""));
        snapshot.put("city", blankToDefault(position.getCity(), ""));
        snapshot.put("experienceRequirement", blankToDefault(position.getExperienceRequirement(), ""));
        snapshot.put("educationRequirement", blankToDefault(position.getEducationRequirement(), ""));
        snapshot.set("skillTags", jsonArray(position.getSkillTags()));
        return write(snapshot);
    }

    private String resumeSnapshot(CandidateResume resume, CandidateResumeAnalysis analysis, String profile) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("id", resume.getId());
        snapshot.put("parseVersion", version(resume));
        snapshot.put("summary", blankToDefault(resume.getSummary(), ""));
        snapshot.set("skills", jsonArray(resume.getSkills()));
        if (StringUtils.hasText(profile)) {
            try {
                JsonNode profileNode = objectMapper.readTree(profile);
                if (profileNode != null && profileNode.isObject()) snapshot.set("profile", profileNode);
            } catch (JsonProcessingException ignored) {
                // The AI input remains available to the task; an invalid snapshot is not a reason to fail matching.
            }
        }
        if (analysis != null) snapshot.put("analysisId", analysis.getId());
        return write(snapshot);
    }

    private ArrayNode jsonArray(String json) {
        ArrayNode values = objectMapper.createArrayNode();
        parseStringList(json).forEach(values::add);
        return values;
    }

    private RuleScore calculateRuleScore(JobPosition position, CandidateResume resume) {
        List<String> required = parseStringList(position.getSkillTags());
        Set<String> requiredSet = required.stream().map(this::normalized).filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> resumeSet = parseStringList(resume.getSkills()).stream().map(this::normalized)
                .filter(StringUtils::hasText).collect(Collectors.toCollection(HashSet::new));
        List<String> matched = required.stream().filter(skill -> resumeSet.contains(normalized(skill))).distinct().limit(MAX_DETAIL_ITEMS).toList();
        BigDecimal coverage = requiredSet.isEmpty() ? BigDecimal.valueOf(60)
                : BigDecimal.valueOf(matched.size()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(requiredSet.size()), 2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        int evidence = StringUtils.hasText(resume.getSummary()) ? 40 : 15;
        if (!resumeSet.isEmpty()) evidence += 30;
        if (SUCCESS.equals(resume.getParseStatus())) evidence += 30;
        BigDecimal score = coverage.multiply(BigDecimal.valueOf(0.70)).add(BigDecimal.valueOf(evidence).multiply(BigDecimal.valueOf(0.30)))
                .setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        return new RuleScore(score, matched);
    }

    private String normalized(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {}).stream()
                    .filter(StringUtils::hasText).map(String::trim).toList();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private ArrayNode normalizeList(JsonNode raw) {
        ArrayNode values = objectMapper.createArrayNode();
        if (!raw.isArray()) return values;
        raw.forEach(item -> {
            String value = item.isObject() ? item.path("text").asText("") : item.asText("");
            value = trim(value.trim(), 240);
            if (StringUtils.hasText(value) && values.size() < MAX_DETAIL_ITEMS) values.add(value);
        });
        return values;
    }

    private String confidence(String value) {
        String normalized = value == null ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        return Set.of("HIGH", "MEDIUM", "LOW").contains(normalized) ? normalized : "MEDIUM";
    }

    private void copyList(ObjectNode target, JsonNode source, String field) {
        ArrayNode values = target.putArray(field);
        JsonNode raw = source.path(field);
        if (!raw.isArray()) return;
        raw.forEach(item -> {
            String text = trim(item.asText().trim(), 240);
            if (!text.isBlank() && values.size() < MAX_DETAIL_ITEMS) values.add(text);
        });
    }

    private CandidateResumeAnalysis latestSuccess(Long resumeId, int version) {
        return analysisMapper.selectOne(new LambdaQueryWrapper<CandidateResumeAnalysis>()
                .eq(CandidateResumeAnalysis::getResumeId, resumeId)
                .eq(CandidateResumeAnalysis::getAnalysisVersion, version)
                .eq(CandidateResumeAnalysis::getStatus, SUCCESS)
                .last("LIMIT 1"));
    }

    private String resumeFacts(CandidateResume resume) {
        return "候选人画像：" + blankToDefault(resume.getSummary(), "未提供") + "；技能：" + blankToDefault(resume.getSkills(), "未提供");
    }

    private boolean isReady(CandidateResume resume) {
        return resume.getParseStatus() == null || "MANUAL".equals(resume.getParseStatus()) || SUCCESS.equals(resume.getParseStatus());
    }

    private String output(Long applicationId, int version, int evaluationVersion, BigDecimal score, boolean stale) {
        return write(java.util.Map.of("applicationId", applicationId, "resumeVersion", version,
                "evaluationVersion", evaluationVersion, "score", score == null ? 0 : score, "stale", stale));
    }

    private record RuleScore(BigDecimal score, List<String> matchedSkills) {
    }

    private JsonNode tree(String payload) {
        try {
            return objectMapper.readTree(payload == null ? "{}" : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("岗位匹配任务参数损坏", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存岗位匹配结果", exception);
        }
    }

    private String safeError(RuntimeException exception) {
        if (exception instanceof BusinessException) {
            return trim(exception.getMessage(), 300);
        }
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) return "岗位匹配失败，请稍后重试";
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("http 401") || normalized.contains("http 403")
                || normalized.contains("api key") || normalized.contains("未找到可用的大模型配置")) {
            return "岗位匹配服务未正确配置，请联系管理员";
        }
        if (normalized.contains("http 429") || normalized.contains("超时") || normalized.contains("timed out")
                || normalized.contains("请求失败") || normalized.contains("调用 deepseek api")) {
            return "岗位匹配服务暂时繁忙，请稍后重试";
        }
        return "岗位匹配失败，请检查简历和岗位信息后重试";
    }

    private static int version(CandidateResume resume) { return resume.getParseVersion() == null ? 0 : resume.getParseVersion(); }
    private static int value(Integer value) { return value == null ? 0 : value; }
    private static String blankToDefault(String value, String fallback) { return StringUtils.hasText(value) ? value : fallback; }
    private static String trim(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
}
