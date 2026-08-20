package com.tyut.aiinterview.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.ai.AiGenerationContext;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.RecruitmentAiCostReservation;
import com.tyut.aiinterview.domain.RecruitmentAiEvalRun;
import com.tyut.aiinterview.domain.RecruitmentAiEvalSuite;
import com.tyut.aiinterview.domain.RecruitmentAiGovernanceEvent;
import com.tyut.aiinterview.domain.RecruitmentAiPolicy;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiCostReservationMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalRunMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalSuiteMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiGovernanceEventMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiPolicyMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class RecruitmentAiGovernanceService {
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int RESERVATION_TTL_MINUTES = 30;

    private final RecruitmentAiPolicyMapper policyMapper;
    private final RecruitmentAiEvalSuiteMapper suiteMapper;
    private final RecruitmentAiEvalRunMapper runMapper;
    private final RecruitmentAiCostReservationMapper costMapper;
    private final RecruitmentAiGovernanceEventMapper eventMapper;
    private final JobApplicationMapper applicationMapper;
    private final RecruitmentSensitiveDataRedactor redactor;
    private final TransactionTemplate transactions;

    public RecruitmentAiGovernanceService(RecruitmentAiPolicyMapper policyMapper,
                                          RecruitmentAiEvalSuiteMapper suiteMapper,
                                          RecruitmentAiEvalRunMapper runMapper,
                                          RecruitmentAiCostReservationMapper costMapper,
                                          RecruitmentAiGovernanceEventMapper eventMapper,
                                          JobApplicationMapper applicationMapper,
                                          RecruitmentSensitiveDataRedactor redactor,
                                          PlatformTransactionManager transactionManager) {
        this.policyMapper = policyMapper;
        this.suiteMapper = suiteMapper;
        this.runMapper = runMapper;
        this.costMapper = costMapper;
        this.eventMapper = eventMapper;
        this.applicationMapper = applicationMapper;
        this.redactor = redactor;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Permit authorize(AiGenerationContext context, String promptCode, Integer promptVersion,
                            String provider, String model, int inputChars, Integer requestedOutputTokens) {
        GovernanceTarget target = target(context);
        if (!target.governed()) return Permit.ungoverned();
        Authorization authorization = transactions.execute(status -> authorizeLocked(target, context, promptCode,
                promptVersion, provider, model, inputChars, requestedOutputTokens));
        if (authorization == null) throw new IllegalStateException("招聘 AI 治理事务未返回结果");
        if (authorization.blocked()) {
            event(target.companyId(), authorization.policyId(), "RUNTIME_GATE", context.generationType(), "BLOCKED",
                    authorization.reasonCode(), authorization.message());
            throw new AiGovernanceException(authorization.reasonCode(), authorization.message());
        }
        Permit permit = authorization.permit();
        event(target.companyId(), permit.policyId(), "RUNTIME_GATE", context.generationType(), "ALLOWED",
                "POLICY_ALLOWED", context.governanceEvaluation() ? "治理评测调用已通过运行门禁" : "招聘 AI 调用已通过运行门禁");
        return permit;
    }

    private Authorization authorizeLocked(GovernanceTarget target, AiGenerationContext context, String promptCode,
                                          Integer promptVersion, String provider, String model, int inputChars,
                                          Integer requestedOutputTokens) {
        RecruitmentAiPolicy global = policyMapper.selectForUpdate("GLOBAL");
        if (global == null) return Authorization.block("POLICY_MISSING", "招聘 AI 全局治理策略不存在", null);
        RecruitmentAiPolicy tenant = target.companyId() == null ? null
                : policyMapper.selectForUpdate("COMPANY:" + target.companyId());
        EffectivePolicy policy = merge(global, tenant);
        if (!policy.aiEnabled()) return Authorization.block("AI_DISABLED", "招聘 AI 已被治理策略停用", policy.policyId());
        if (policy.emergencyStop()) return Authorization.block("EMERGENCY_STOP",
                "招聘 AI 紧急停用开关已开启" + suffix(policy.emergencyReason()), policy.policyId());

        // Recruitment prompts are primarily Chinese; reserving one token per UTF-16 character is intentionally
        // conservative and prevents the budget gate from under-reserving compared with English-only heuristics.
        int estimatedInputTokens = Math.max(1, Math.max(0, inputChars));
        int estimatedOutputTokens = Math.max(1, requestedOutputTokens == null ? 1024 : requestedOutputTokens);
        if ((long) estimatedInputTokens + estimatedOutputTokens > policy.perRequestTokenLimit()) {
            return Authorization.block("REQUEST_TOKEN_LIMIT", "本次模型请求超过招聘 AI 单次 Token 上限", policy.policyId());
        }

        if (!context.governanceEvaluation() && policy.evaluationGateRequired()) {
            String evaluationType = evaluationType(promptCode);
            if (evaluationType != null) {
                Authorization gate = evaluationGate(policy, evaluationType, promptCode, promptVersion, provider, model);
                if (gate != null) return gate;
            }
        }

        int released = costMapper.releaseExpired(LocalDateTime.now().minusMinutes(RESERVATION_TTL_MINUTES));
        if (released > 0) {
            event(target.companyId(), policy.policyId(), "COST_RESERVATION", context.generationType(), "CHANGED",
                    "STALE_RESERVATIONS_RELEASED", "已释放 " + released + " 条超时成本预留");
        }
        BigDecimal estimate = estimatedCost(estimatedInputTokens, estimatedOutputTokens,
                policy.inputCostPerMillionUsd(), policy.outputCostPerMillionUsd());
        Authorization budget = budgetGate(global, tenant, target.companyId(), estimate);
        if (budget != null) return budget;

        RecruitmentAiCostReservation reservation = new RecruitmentAiCostReservation();
        reservation.setCompanyId(target.companyId());
        reservation.setGenerationType(context.generationType());
        reservation.setPromptCode(promptCode);
        reservation.setPromptVersion(promptVersion);
        reservation.setProvider(provider);
        reservation.setModel(model);
        reservation.setStatus("RESERVED");
        reservation.setEstimatedInputTokens(estimatedInputTokens);
        reservation.setEstimatedOutputTokens(estimatedOutputTokens);
        reservation.setEstimatedCostUsd(estimate);
        reservation.setCreatedAt(LocalDateTime.now());
        costMapper.insert(reservation);
        return Authorization.allow(new Permit(true, target.companyId(), policy.policyId(),
                target.companyId() == null ? "GLOBAL" : "COMPANY", reservation.getId(), estimate,
                policy.inputCostPerMillionUsd(), policy.outputCostPerMillionUsd()));
    }

    private Authorization evaluationGate(EffectivePolicy policy, String evaluationType, String promptCode,
                                         Integer promptVersion, String provider, String model) {
        RecruitmentAiEvalSuite suite = suiteMapper.selectOne(new LambdaQueryWrapper<RecruitmentAiEvalSuite>()
                .eq(RecruitmentAiEvalSuite::getEvaluationType, evaluationType)
                .eq(RecruitmentAiEvalSuite::getEnabled, 1).last("LIMIT 1"));
        if (suite == null) return Authorization.block("EVALUATION_SUITE_MISSING",
                "招聘 AI 缺少 " + evaluationType + " 评测集", policy.policyId());
        RecruitmentAiEvalRun run = runMapper.selectValidGate(suite.getId(), provider, model, promptCode, promptVersion,
                LocalDateTime.now().minusDays(policy.evaluationValidDays()));
        if (run == null) return Authorization.block("EVALUATION_GATE_MISSING",
                "当前模型与 Prompt 版本尚未通过有效的 " + suite.getName(), policy.policyId());
        if (below(run.getPassRate(), policy.minimumPassRate())
                || above(run.getMaximumScoreDrift(), policy.maximumScoreDrift())
                || above(run.getMaximumFairnessGap(), policy.maximumFairnessGap())) {
            return Authorization.block("EVALUATION_GATE_REGRESSION",
                    "最近评测结果未达到当前回归或公平性阈值", policy.policyId());
        }
        return null;
    }

    private Authorization budgetGate(RecruitmentAiPolicy global, RecruitmentAiPolicy tenant, Long companyId,
                                     BigDecimal estimate) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        BigDecimal globalDay = value(costMapper.sumActiveCostSince(null, dayStart));
        BigDecimal globalMonth = value(costMapper.sumActiveCostSince(null, monthStart));
        if (exceeds(globalDay, estimate, global.getDailyCostLimitUsd())) {
            return Authorization.block("GLOBAL_DAILY_BUDGET", "招聘 AI 全局日成本上限已达到", global.getId());
        }
        if (exceeds(globalMonth, estimate, global.getMonthlyCostLimitUsd())) {
            return Authorization.block("GLOBAL_MONTHLY_BUDGET", "招聘 AI 全局月成本上限已达到", global.getId());
        }
        if (tenant != null && companyId != null) {
            BigDecimal tenantDay = value(costMapper.sumActiveCostSince(companyId, dayStart));
            BigDecimal tenantMonth = value(costMapper.sumActiveCostSince(companyId, monthStart));
            if (exceeds(tenantDay, estimate, tenant.getDailyCostLimitUsd())) {
                return Authorization.block("TENANT_DAILY_BUDGET", "当前企业招聘 AI 日成本上限已达到", tenant.getId());
            }
            if (exceeds(tenantMonth, estimate, tenant.getMonthlyCostLimitUsd())) {
                return Authorization.block("TENANT_MONTHLY_BUDGET", "当前企业招聘 AI 月成本上限已达到", tenant.getId());
            }
        }
        return null;
    }

    @Transactional
    public Settlement settle(Permit permit, String requestId, Integer promptTokens,
                             Integer completionTokens, Integer totalTokens) {
        if (permit == null || !permit.governed() || permit.reservationId() == null) return Settlement.none();
        RecruitmentAiCostReservation reservation = costMapper.selectById(permit.reservationId());
        if (reservation == null || !"RESERVED".equals(reservation.getStatus())) return Settlement.none();
        int input = promptTokens == null ? reservation.getEstimatedInputTokens() : Math.max(0, promptTokens);
        int output = completionTokens == null ? reservation.getEstimatedOutputTokens() : Math.max(0, completionTokens);
        int total = totalTokens == null ? input + output : Math.max(0, totalTokens);
        BigDecimal actual = estimatedCost(input, output, permit.inputCostPerMillionUsd(), permit.outputCostPerMillionUsd());
        reservation.setStatus("SETTLED");
        reservation.setActualTokens(total);
        reservation.setActualCostUsd(actual);
        reservation.setGenerationRequestId(requestId);
        reservation.setSettledAt(LocalDateTime.now());
        costMapper.updateById(reservation);
        return new Settlement(actual, total);
    }

    @Transactional
    public void release(Permit permit, String requestId) {
        if (permit == null || !permit.governed() || permit.reservationId() == null) return;
        RecruitmentAiCostReservation reservation = costMapper.selectById(permit.reservationId());
        if (reservation == null || !"RESERVED".equals(reservation.getStatus())) return;
        reservation.setStatus("RELEASED");
        reservation.setGenerationRequestId(requestId);
        reservation.setSettledAt(LocalDateTime.now());
        costMapper.updateById(reservation);
    }

    public RecruitmentSensitiveDataRedactor.Result prepareSensitiveInput(Long companyId, String input) {
        RecruitmentSensitiveDataRedactor.Result result = redactor.redact(input);
        if (result.detected() && "BLOCK_ON_DETECTION".equals(effectivePolicy(companyId).sensitiveDataMode())) {
            event(companyId, effectivePolicy(companyId).policyId(), "SENSITIVE_DATA", null, "BLOCKED",
                    "SENSITIVE_DATA_DETECTED", "检测到敏感字段，当前策略禁止发送至模型");
            throw new AiGovernanceException("SENSITIVE_DATA_DETECTED", "检测到敏感字段，当前招聘 AI 策略禁止模型处理");
        }
        return result;
    }

    public RecruitmentSensitiveDataRedactor.Result prepareSensitiveJson(Long companyId, String input) {
        RecruitmentSensitiveDataRedactor.Result result = redactor.redactJson(input);
        if (result.detected() && "BLOCK_ON_DETECTION".equals(effectivePolicy(companyId).sensitiveDataMode())) {
            event(companyId, effectivePolicy(companyId).policyId(), "SENSITIVE_DATA", null, "BLOCKED",
                    "SENSITIVE_DATA_DETECTED", "检测到结构化敏感字段，当前策略禁止发送至模型");
            throw new AiGovernanceException("SENSITIVE_DATA_DETECTED", "检测到敏感字段，当前招聘 AI 策略禁止模型处理");
        }
        return result;
    }

    public boolean requiresHumanReview(Long companyId, BigDecimal score, String confidence) {
        EffectivePolicy policy = effectivePolicy(companyId);
        return switch (policy.humanReviewMode()) {
            case "ADVERSE_ONLY" -> score == null || score.compareTo(policy.adverseScoreThreshold()) < 0;
            case "LOW_CONFIDENCE" -> !"HIGH".equalsIgnoreCase(confidence);
            default -> true;
        };
    }

    public Long companyIdForInterview(Long interviewId) {
        if (interviewId == null) return null;
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getInterviewId, interviewId).last("LIMIT 1"));
        return application == null ? null : application.getCompanyId();
    }

    public EffectivePolicy effectivePolicy(Long companyId) {
        RecruitmentAiPolicy global = policyMapper.selectOne(new LambdaQueryWrapper<RecruitmentAiPolicy>()
                .eq(RecruitmentAiPolicy::getScopeKey, "GLOBAL").last("LIMIT 1"));
        if (global == null) throw new IllegalStateException("招聘 AI 全局治理策略不存在");
        RecruitmentAiPolicy tenant = companyId == null ? null : policyMapper.selectByCompanyId(companyId);
        return merge(global, tenant);
    }

    public CostUsage costUsage(Long companyId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        return new CostUsage(value(costMapper.sumActiveCostSince(companyId, dayStart)),
                value(costMapper.sumActiveCostSince(companyId, monthStart)));
    }

    public List<RecruitmentAiGovernanceEvent> recentEvents(int limit) {
        return eventMapper.selectList(new LambdaQueryWrapper<RecruitmentAiGovernanceEvent>()
                .orderByDesc(RecruitmentAiGovernanceEvent::getId).last("LIMIT " + Math.max(1, Math.min(100, limit))));
    }

    public void recordAdministrativeEvent(Long companyId, Long policyId, String eventType,
                                          String reasonCode, String summary) {
        event(companyId, policyId, eventType, null, "CHANGED", reasonCode, summary);
    }

    private GovernanceTarget target(AiGenerationContext context) {
        if (context == null || !context.recruitmentGoverned()) return GovernanceTarget.none();
        Long companyId = context.companyId();
        if (companyId == null && context.interviewId() != null) {
            JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getInterviewId, context.interviewId()).last("LIMIT 1"));
            if (application == null && !context.governanceEvaluation()) return GovernanceTarget.none();
            companyId = application == null ? null : application.getCompanyId();
        }
        return new GovernanceTarget(true, companyId);
    }

    private EffectivePolicy merge(RecruitmentAiPolicy global, RecruitmentAiPolicy tenant) {
        if (tenant == null) return policy(global, global);
        return new EffectivePolicy(tenant.getId(), global.getAiEnabled() == 1 && tenant.getAiEnabled() == 1,
                global.getEmergencyStop() == 1 || tenant.getEmergencyStop() == 1,
                StringUtils.hasText(tenant.getEmergencyReason()) ? tenant.getEmergencyReason() : global.getEmergencyReason(),
                global.getEvaluationGateRequired() == 1 || tenant.getEvaluationGateRequired() == 1,
                Math.min(global.getEvaluationValidDays(), tenant.getEvaluationValidDays()),
                max(global.getMinimumPassRate(), tenant.getMinimumPassRate()),
                min(global.getMaximumScoreDrift(), tenant.getMaximumScoreDrift()),
                min(global.getMaximumFairnessGap(), tenant.getMaximumFairnessGap()),
                stricterReview(global.getHumanReviewMode(), tenant.getHumanReviewMode()),
                max(global.getAdverseScoreThreshold(), tenant.getAdverseScoreThreshold()),
                "BLOCK_ON_DETECTION".equals(global.getSensitiveDataMode()) || "BLOCK_ON_DETECTION".equals(tenant.getSensitiveDataMode())
                        ? "BLOCK_ON_DETECTION" : "REDACT",
                Math.min(global.getPerRequestTokenLimit(), tenant.getPerRequestTokenLimit()),
                max(global.getInputCostPerMillionUsd(), tenant.getInputCostPerMillionUsd()),
                max(global.getOutputCostPerMillionUsd(), tenant.getOutputCostPerMillionUsd()));
    }

    private EffectivePolicy policy(RecruitmentAiPolicy source, RecruitmentAiPolicy pricing) {
        return new EffectivePolicy(source.getId(), source.getAiEnabled() == 1, source.getEmergencyStop() == 1,
                source.getEmergencyReason(), source.getEvaluationGateRequired() == 1,
                source.getEvaluationValidDays(), source.getMinimumPassRate(), source.getMaximumScoreDrift(),
                source.getMaximumFairnessGap(), source.getHumanReviewMode(), source.getAdverseScoreThreshold(),
                source.getSensitiveDataMode(), source.getPerRequestTokenLimit(), pricing.getInputCostPerMillionUsd(),
                pricing.getOutputCostPerMillionUsd());
    }

    private void event(Long companyId, Long policyId, String eventType, String generationType, String decision,
                       String reasonCode, String summary) {
        RecruitmentAiGovernanceEvent event = new RecruitmentAiGovernanceEvent();
        event.setCompanyId(companyId);
        event.setPolicyId(policyId);
        event.setEventType(eventType);
        event.setGenerationType(generationType);
        event.setDecision(decision);
        event.setReasonCode(reasonCode);
        event.setSummary(trim(summary, 500));
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private String evaluationType(String promptCode) {
        if ("resume.analysis".equals(promptCode)) return "RESUME_ANALYSIS";
        if ("recruitment.job_match".equals(promptCode)) return "JOB_MATCH";
        if ("report.answer_evaluation".equals(promptCode)) return "INTERVIEW_SCORING";
        return null;
    }

    private BigDecimal estimatedCost(int inputTokens, int outputTokens, BigDecimal inputRate, BigDecimal outputRate) {
        return BigDecimal.valueOf(inputTokens).multiply(value(inputRate)).divide(ONE_MILLION, 8, RoundingMode.HALF_UP)
                .add(BigDecimal.valueOf(outputTokens).multiply(value(outputRate)).divide(ONE_MILLION, 8, RoundingMode.HALF_UP))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private boolean exceeds(BigDecimal current, BigDecimal estimate, BigDecimal limit) {
        return current.add(estimate).compareTo(value(limit)) > 0;
    }

    private boolean below(BigDecimal value, BigDecimal threshold) {
        return value == null || value.compareTo(threshold) < 0;
    }

    private boolean above(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) > 0;
    }

    private String stricterReview(String left, String right) {
        return List.of(left, right).stream().filter(Objects::nonNull)
                .max(Comparator.comparingInt(this::reviewRank)).orElse("ALL");
    }

    private int reviewRank(String mode) {
        return switch (mode == null ? "ALL" : mode.toUpperCase(Locale.ROOT)) {
            case "ADVERSE_ONLY" -> 1;
            case "LOW_CONFIDENCE" -> 2;
            default -> 3;
        };
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) { return left.min(right); }
    private BigDecimal max(BigDecimal left, BigDecimal right) { return left.max(right); }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String suffix(String value) { return StringUtils.hasText(value) ? "：" + value : ""; }
    private String trim(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }

    public record Permit(boolean governed, Long companyId, Long policyId, String governanceScope,
                         Long reservationId, BigDecimal estimatedCostUsd,
                         BigDecimal inputCostPerMillionUsd, BigDecimal outputCostPerMillionUsd) {
        public static Permit ungoverned() {
            return new Permit(false, null, null, null, null, null, null, null);
        }
    }

    public record Settlement(BigDecimal actualCostUsd, Integer actualTokens) {
        public static Settlement none() { return new Settlement(null, null); }
    }

    public record EffectivePolicy(Long policyId, boolean aiEnabled, boolean emergencyStop, String emergencyReason,
                                  boolean evaluationGateRequired, int evaluationValidDays,
                                  BigDecimal minimumPassRate, BigDecimal maximumScoreDrift,
                                  BigDecimal maximumFairnessGap, String humanReviewMode,
                                  BigDecimal adverseScoreThreshold, String sensitiveDataMode,
                                  int perRequestTokenLimit, BigDecimal inputCostPerMillionUsd,
                                  BigDecimal outputCostPerMillionUsd) {
    }

    public record CostUsage(BigDecimal todayUsd, BigDecimal monthUsd) {
    }

    private record GovernanceTarget(boolean governed, Long companyId) {
        private static GovernanceTarget none() { return new GovernanceTarget(false, null); }
    }

    private record Authorization(boolean blocked, String reasonCode, String message, Long policyId, Permit permit) {
        private static Authorization block(String code, String message, Long policyId) {
            return new Authorization(true, code, message, policyId, null);
        }
        private static Authorization allow(Permit permit) {
            return new Authorization(false, null, null, permit.policyId(), permit);
        }
    }
}
