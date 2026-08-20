package com.tyut.aiinterview.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tyut.aiinterview.ai.AiGenerationContext;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.RecruitmentAiEvalCase;
import com.tyut.aiinterview.domain.RecruitmentAiEvalResult;
import com.tyut.aiinterview.domain.RecruitmentAiEvalRun;
import com.tyut.aiinterview.domain.RecruitmentAiEvalSuite;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalCaseMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalResultMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalRunMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalSuiteMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class RecruitmentAiEvaluationService {
    private final RecruitmentAiEvalSuiteMapper suiteMapper;
    private final RecruitmentAiEvalCaseMapper caseMapper;
    private final RecruitmentAiEvalRunMapper runMapper;
    private final RecruitmentAiEvalResultMapper resultMapper;
    private final RecruitmentAiGovernanceService governanceService;
    private final RecruitmentSensitiveDataRedactor redactor;
    private final DeepSeekGateway gateway;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public RecruitmentAiEvaluationService(RecruitmentAiEvalSuiteMapper suiteMapper,
                                          RecruitmentAiEvalCaseMapper caseMapper,
                                          RecruitmentAiEvalRunMapper runMapper,
                                          RecruitmentAiEvalResultMapper resultMapper,
                                          RecruitmentAiGovernanceService governanceService,
                                          RecruitmentSensitiveDataRedactor redactor,
                                          DeepSeekGateway gateway, ObjectMapper objectMapper,
                                          @Qualifier("aiGovernanceEvaluationExecutor") Executor executor) {
        this.suiteMapper = suiteMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.governanceService = governanceService;
        this.redactor = redactor;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Transactional
    public RecruitmentAiEvalRun start(Long suiteId, Long operatorId) {
        RecruitmentAiEvalSuite suite = suiteMapper.selectForUpdate(suiteId);
        if (suite == null || !Integer.valueOf(1).equals(suite.getEnabled())) {
            throw BusinessException.notFound("招聘 AI 评测集不存在");
        }
        RecruitmentAiEvalRun running = runMapper.selectOne(new LambdaQueryWrapper<RecruitmentAiEvalRun>()
                .eq(RecruitmentAiEvalRun::getSuiteId, suiteId)
                .eq(RecruitmentAiEvalRun::getStatus, "RUNNING").last("LIMIT 1"));
        if (running != null && running.getStartedAt() != null
                && running.getStartedAt().isBefore(LocalDateTime.now().minusMinutes(30))) {
            running.setStatus("FAILED");
            running.setFailureSummary("评测运行超过 30 分钟未完成，已自动回收");
            running.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(running);
            running = null;
        }
        if (running != null) throw BusinessException.badRequest("该评测集已有运行中的任务");
        long caseCount = caseMapper.selectCount(new LambdaQueryWrapper<RecruitmentAiEvalCase>()
                .eq(RecruitmentAiEvalCase::getSuiteId, suiteId).eq(RecruitmentAiEvalCase::getEnabled, 1));
        if (caseCount == 0) throw BusinessException.badRequest("评测集没有已启用的评测用例");
        RecruitmentAiEvalRun run = new RecruitmentAiEvalRun();
        run.setSuiteId(suiteId);
        run.setStatus("RUNNING");
        run.setPromptCode(suite.getPromptCode());
        run.setCaseCount(Math.toIntExact(caseCount));
        run.setPassedCaseCount(0);
        run.setStartedBy(operatorId);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        Long runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    executor.execute(() -> execute(runId));
                } catch (RuntimeException exception) {
                    failRun(runId, "评测执行队列不可用：" + safeError(exception));
                }
            }
        });
        return run;
    }

    public void execute(Long runId) {
        RecruitmentAiEvalRun run = runMapper.selectById(runId);
        if (run == null || !"RUNNING".equals(run.getStatus())) return;
        RecruitmentAiEvalSuite suite = suiteMapper.selectById(run.getSuiteId());
        List<RecruitmentAiEvalCase> cases = caseMapper.selectList(new LambdaQueryWrapper<RecruitmentAiEvalCase>()
                .eq(RecruitmentAiEvalCase::getSuiteId, run.getSuiteId())
                .eq(RecruitmentAiEvalCase::getEnabled, 1).orderByAsc(RecruitmentAiEvalCase::getId));
        List<CaseOutcome> outcomes = new ArrayList<>();
        try {
            for (RecruitmentAiEvalCase evaluationCase : cases) {
                CaseOutcome outcome = executeCase(run, suite, evaluationCase);
                outcomes.add(outcome);
                if (run.getProvider() == null && outcome.output() != null) {
                    run.setProvider(outcome.output().provider());
                    run.setModel(outcome.output().model());
                    run.setPromptCode(outcome.output().promptCode());
                    run.setPromptVersion(outcome.output().promptVersion());
                }
            }
            finish(run, cases, outcomes);
        } catch (RuntimeException exception) {
            failRun(run.getId(), safeError(exception));
        }
    }

    private CaseOutcome executeCase(RecruitmentAiEvalRun run, RecruitmentAiEvalSuite suite,
                                    RecruitmentAiEvalCase evaluationCase) {
        long started = System.nanoTime();
        RecruitmentAiEvalResult result = new RecruitmentAiEvalResult();
        result.setRunId(run.getId());
        result.setCaseId(evaluationCase.getId());
        result.setCreatedAt(LocalDateTime.now());
        try {
            JsonNode input = objectMapper.readTree(evaluationCase.getInputJson());
            EvalOutput output = generate(suite, input, run.getStartedBy());
            BigDecimal score = score(suite.getEvaluationType(), output.content());
            BigDecimal drift = score == null || evaluationCase.getBaselineScore() == null ? null
                    : score.subtract(evaluationCase.getBaselineScore()).abs().setScale(2, RoundingMode.HALF_UP);
            Assertions assertions = assertions(suite.getEvaluationType(), evaluationCase, output.content(), score);
            result.setStatus(assertions.passed() ? "PASSED" : "FAILED");
            result.setActualScore(score);
            result.setScoreDrift(drift);
            result.setResponseHash(hash(output.content().toString()));
            result.setAssertionSummary(objectMapper.writeValueAsString(assertions));
            result.setLatencyMs(Duration.ofNanos(System.nanoTime() - started).toMillis());
            resultMapper.insert(result);
            return new CaseOutcome(evaluationCase, result, output);
        } catch (RuntimeException | JsonProcessingException exception) {
            result.setStatus("ERROR");
            result.setErrorMessage(safeError(exception));
            result.setLatencyMs(Duration.ofNanos(System.nanoTime() - started).toMillis());
            resultMapper.insert(result);
            return new CaseOutcome(evaluationCase, result, null);
        }
    }

    private EvalOutput generate(RecruitmentAiEvalSuite suite, JsonNode input, Long operatorId) {
        AiGenerationContext context = AiGenerationContext.evaluation("AI_GOVERNANCE_" + suite.getEvaluationType(), operatorId);
        return switch (suite.getEvaluationType()) {
            case "RESUME_ANALYSIS" -> output(gateway.analyzeResume(
                    redactor.redact(text(input, "resumeText")).value(), text(input, "targetRole"), context));
            case "JOB_MATCH" -> output(gateway.matchResumeToJob(text(input, "jobTitle"),
                    redactor.redact(text(input, "jobDescription")).value(),
                    redactor.redact(text(input, "requirements")).value(), text(input, "skillTags"),
                    redactor.redactJson(text(input, "resumeProfile")).value(), text(input, "resumeSkills"),
                    redactor.redact(text(input, "resumeText")).value(), context));
            case "INTERVIEW_SCORING" -> output(gateway.evaluateAnswer(
                    redactor.redact(text(input, "question")).value(),
                    redactor.redact(text(input, "referenceAnswer")).value(),
                    redactor.redact(text(input, "candidateAnswer")).value(), context, null));
            default -> throw new IllegalStateException("不支持的招聘 AI 评测类型");
        };
    }

    private EvalOutput output(DeepSeekGateway.Generated<JsonNode> generated) {
        return new EvalOutput(generated.content(), generated.providerCode(), generated.model(),
                generated.promptCode(), generated.promptVersion());
    }

    private Assertions assertions(String type, RecruitmentAiEvalCase evaluationCase, JsonNode content,
                                  BigDecimal score) {
        boolean schema = schemaValid(type, content);
        boolean withinRange = score == null || (minimum(score, evaluationCase.getExpectedScoreMin())
                && maximum(score, evaluationCase.getExpectedScoreMax()));
        String serialized = content.toString().toLowerCase();
        List<String> missing = terms(evaluationCase.getRequiredTerms()).stream()
                .filter(term -> !serialized.contains(term.toLowerCase())).toList();
        List<String> forbidden = terms(evaluationCase.getForbiddenTerms()).stream()
                .filter(term -> serialized.contains(term.toLowerCase())).toList();
        return new Assertions(schema, withinRange, missing.isEmpty(), forbidden.isEmpty(), missing, forbidden);
    }

    private void finish(RecruitmentAiEvalRun run, List<RecruitmentAiEvalCase> cases, List<CaseOutcome> outcomes) {
        int passed = (int) outcomes.stream().filter(item -> "PASSED".equals(item.result().getStatus())).count();
        BigDecimal passRate = cases.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(passed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(cases.size()), 2, RoundingMode.HALF_UP);
        BigDecimal maxDrift = outcomes.stream().map(item -> item.result().getScoreDrift()).filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal fairnessGap = fairnessGap(outcomes);
        RecruitmentAiGovernanceService.EffectivePolicy policy = governanceService.effectivePolicy(null);
        List<EvalOutput> outputs = outcomes.stream().map(CaseOutcome::output).filter(java.util.Objects::nonNull).toList();
        boolean targetConsistent = outputs.size() == cases.size() && !outputs.isEmpty()
                && outputs.stream().allMatch(output -> sameTarget(outputs.get(0), output));
        boolean pass = passed == cases.size() && passRate.compareTo(policy.minimumPassRate()) >= 0
                && maxDrift.compareTo(policy.maximumScoreDrift()) <= 0
                && fairnessGap.compareTo(policy.maximumFairnessGap()) <= 0
                && targetConsistent && run.getProvider() != null && run.getPromptVersion() != null;
        run.setCaseCount(cases.size());
        run.setPassedCaseCount(passed);
        run.setPassRate(passRate);
        run.setMaximumScoreDrift(maxDrift);
        run.setMaximumFairnessGap(fairnessGap);
        run.setStatus(pass ? "PASSED" : "FAILED");
        run.setFailureSummary(pass ? null : targetConsistent
                ? "评测未达到用例、回归漂移或公平性门禁阈值"
                : "评测期间 Provider、模型或 Prompt 版本发生变化，结果不可作为门禁证据");
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    private BigDecimal fairnessGap(List<CaseOutcome> outcomes) {
        Map<String, List<BigDecimal>> scores = new HashMap<>();
        outcomes.forEach(outcome -> {
            if (StringUtils.hasText(outcome.evaluationCase().getPairKey())
                    && outcome.result().getActualScore() != null) {
                scores.computeIfAbsent(outcome.evaluationCase().getPairKey(), ignored -> new ArrayList<>())
                        .add(outcome.result().getActualScore());
            }
        });
        return scores.values().stream().filter(values -> values.size() >= 2).map(values -> {
            BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            return max.subtract(min).abs();
        }).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public RecruitmentAiEvalCase createCase(Long suiteId,
                                            RecruitmentAiGovernanceDtos.EvalCaseRequest request,
                                            Long operatorId) {
        requireSuite(suiteId);
        if (!request.input().isObject()) throw BusinessException.badRequest("评测输入必须是 JSON 对象");
        if (request.expectedScoreMin() != null && request.expectedScoreMax() != null
                && request.expectedScoreMin().compareTo(request.expectedScoreMax()) > 0) {
            throw BusinessException.badRequest("评测分数下限不能高于上限");
        }
        long duplicate = caseMapper.selectCount(new LambdaQueryWrapper<RecruitmentAiEvalCase>()
                .eq(RecruitmentAiEvalCase::getSuiteId, suiteId)
                .eq(RecruitmentAiEvalCase::getCaseCode, request.caseCode().trim()));
        if (duplicate > 0) throw BusinessException.conflict("评测用例编码已存在");
        RecruitmentAiEvalCase item = new RecruitmentAiEvalCase();
        item.setSuiteId(suiteId);
        item.setCaseCode(request.caseCode().trim());
        item.setName(request.name().trim());
        item.setCohortCode(trim(request.cohortCode(), 64));
        item.setPairKey(trim(request.pairKey(), 64));
        item.setInputJson(write(request.input()));
        item.setExpectedScoreMin(request.expectedScoreMin());
        item.setExpectedScoreMax(request.expectedScoreMax());
        item.setBaselineScore(request.baselineScore());
        item.setRequiredTerms(write(request.requiredTerms() == null ? List.of() : request.requiredTerms()));
        item.setForbiddenTerms(write(request.forbiddenTerms() == null ? List.of() : request.forbiddenTerms()));
        item.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        item.setCreatedBy(operatorId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        caseMapper.insert(item);
        return item;
    }

    public List<RecruitmentAiEvalSuite> suites() {
        return suiteMapper.selectList(new LambdaQueryWrapper<RecruitmentAiEvalSuite>()
                .eq(RecruitmentAiEvalSuite::getEnabled, 1).orderByAsc(RecruitmentAiEvalSuite::getId));
    }

    public List<RecruitmentAiEvalCase> cases(Long suiteId) {
        requireSuite(suiteId);
        return caseMapper.selectList(new LambdaQueryWrapper<RecruitmentAiEvalCase>()
                .eq(RecruitmentAiEvalCase::getSuiteId, suiteId).orderByAsc(RecruitmentAiEvalCase::getId));
    }

    public RecruitmentAiEvalRun latestRun(Long suiteId) {
        return runMapper.selectOne(new LambdaQueryWrapper<RecruitmentAiEvalRun>()
                .eq(RecruitmentAiEvalRun::getSuiteId, suiteId).orderByDesc(RecruitmentAiEvalRun::getId).last("LIMIT 1"));
    }

    public GateStatus currentGateStatus(RecruitmentAiEvalSuite suite,
                                        RecruitmentAiGovernanceService.EffectivePolicy policy) {
        DeepSeekGateway.GovernanceTarget target = gateway.currentGovernanceTarget(suite.getPromptCode());
        RecruitmentAiEvalRun run = target.configured() ? runMapper.selectValidGate(suite.getId(), target.provider(),
                target.model(), suite.getPromptCode(), target.promptVersion(),
                LocalDateTime.now().minusDays(policy.evaluationValidDays())) : null;
        boolean ready = run != null && run.getPassRate() != null
                && run.getPassRate().compareTo(policy.minimumPassRate()) >= 0
                && (run.getMaximumScoreDrift() == null
                    || run.getMaximumScoreDrift().compareTo(policy.maximumScoreDrift()) <= 0)
                && (run.getMaximumFairnessGap() == null
                    || run.getMaximumFairnessGap().compareTo(policy.maximumFairnessGap()) <= 0);
        return new GateStatus(ready, target.provider(), target.model(), target.promptVersion());
    }

    public RecruitmentAiEvalRun run(Long id) {
        RecruitmentAiEvalRun run = runMapper.selectById(id);
        if (run == null) throw BusinessException.notFound("招聘 AI 评测运行不存在");
        return run;
    }

    private RecruitmentAiEvalSuite requireSuite(Long suiteId) {
        RecruitmentAiEvalSuite suite = suiteMapper.selectById(suiteId);
        if (suite == null || !Integer.valueOf(1).equals(suite.getEnabled())) {
            throw BusinessException.notFound("招聘 AI 评测集不存在");
        }
        return suite;
    }

    private boolean schemaValid(String type, JsonNode content) {
        if (content == null || !content.isObject()) return false;
        return switch (type) {
            case "RESUME_ANALYSIS" -> StringUtils.hasText(content.path("candidateProfile").asText())
                    && content.path("skills").isArray();
            case "JOB_MATCH" -> content.path("score").canConvertToInt()
                    && content.path("score").asInt(-1) >= 0 && content.path("score").asInt(101) <= 100
                    && StringUtils.hasText(content.path("summary").asText());
            case "INTERVIEW_SCORING" -> Set.of("professionalScore", "expressionScore", "logicScore",
                            "adaptabilityScore", "overallScore")
                    .stream().allMatch(field -> content.path(field).canConvertToInt()
                            && content.path(field).asInt(-1) >= 0 && content.path(field).asInt(101) <= 100);
            default -> false;
        };
    }

    private BigDecimal score(String type, JsonNode content) {
        if ("JOB_MATCH".equals(type)) return BigDecimal.valueOf(content.path("score").asInt(-1));
        if ("INTERVIEW_SCORING".equals(type)) return BigDecimal.valueOf(content.path("overallScore").asInt(-1));
        return null;
    }

    private boolean minimum(BigDecimal score, BigDecimal minimum) { return minimum == null || score.compareTo(minimum) >= 0; }
    private boolean maximum(BigDecimal score, BigDecimal maximum) { return maximum == null || score.compareTo(maximum) <= 0; }

    private List<String> terms(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {}).stream()
                    .filter(StringUtils::hasText).map(String::trim).toList();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private String text(JsonNode input, String field) { return input.path(field).asText(""); }
    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("评测数据序列化失败", exception); }
    }
    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) return "评测执行失败";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
    private boolean sameTarget(EvalOutput left, EvalOutput right) {
        return java.util.Objects.equals(left.provider(), right.provider())
                && java.util.Objects.equals(left.model(), right.model())
                && java.util.Objects.equals(left.promptCode(), right.promptCode())
                && java.util.Objects.equals(left.promptVersion(), right.promptVersion());
    }
    private void failRun(Long runId, String summary) {
        RecruitmentAiEvalRun failed = runMapper.selectById(runId);
        if (failed == null || !"RUNNING".equals(failed.getStatus())) return;
        failed.setStatus("FAILED");
        failed.setFailureSummary(summary);
        failed.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(failed);
    }
    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private record EvalOutput(JsonNode content, String provider, String model, String promptCode, Integer promptVersion) {
    }
    public record GateStatus(boolean ready, String provider, String model, int promptVersion) {
    }
    private record CaseOutcome(RecruitmentAiEvalCase evaluationCase, RecruitmentAiEvalResult result, EvalOutput output) {
    }
    private record Assertions(boolean schemaValid, boolean scoreWithinRange, boolean requiredTermsPresent,
                              boolean forbiddenTermsAbsent, List<String> missingRequiredTerms,
                              List<String> matchedForbiddenTerms) {
        private boolean passed() {
            return schemaValid && scoreWithinRange && requiredTermsPresent && forbiddenTermsAbsent;
        }
    }
}
