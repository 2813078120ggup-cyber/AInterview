package com.tyut.aiinterview.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.JobMatchEvaluation;
import com.tyut.aiinterview.domain.RecruitmentAiEvalCase;
import com.tyut.aiinterview.domain.RecruitmentAiEvalRun;
import com.tyut.aiinterview.domain.RecruitmentAiEvalSuite;
import com.tyut.aiinterview.domain.RecruitmentAiGovernanceEvent;
import com.tyut.aiinterview.domain.RecruitmentAiPolicy;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.JobMatchEvaluationMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalCaseMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiPolicyMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminRecruitmentAiGovernanceService {
    private final RecruitmentAiPolicyMapper policyMapper;
    private final RecruitmentAiEvalCaseMapper caseMapper;
    private final JobMatchEvaluationMapper matchMapper;
    private final ReportMapper reportMapper;
    private final CompanyMapper companyMapper;
    private final RecruitmentAiGovernanceService governanceService;
    private final RecruitmentAiEvaluationService evaluationService;
    private final OperationAuditService auditService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public AdminRecruitmentAiGovernanceService(RecruitmentAiPolicyMapper policyMapper,
                                               RecruitmentAiEvalCaseMapper caseMapper,
                                               JobMatchEvaluationMapper matchMapper,
                                               ReportMapper reportMapper, CompanyMapper companyMapper,
                                               RecruitmentAiGovernanceService governanceService,
                                               RecruitmentAiEvaluationService evaluationService,
                                               OperationAuditService auditService, CurrentUser currentUser,
                                               ObjectMapper objectMapper) {
        this.policyMapper = policyMapper;
        this.caseMapper = caseMapper;
        this.matchMapper = matchMapper;
        this.reportMapper = reportMapper;
        this.companyMapper = companyMapper;
        this.governanceService = governanceService;
        this.evaluationService = evaluationService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    public RecruitmentAiGovernanceDtos.Overview overview() {
        RecruitmentAiPolicy global = requireGlobal();
        List<RecruitmentAiPolicy> tenants = policyMapper.selectList(new LambdaQueryWrapper<RecruitmentAiPolicy>()
                .isNotNull(RecruitmentAiPolicy::getCompanyId).orderByAsc(RecruitmentAiPolicy::getCompanyId));
        RecruitmentAiGovernanceService.EffectivePolicy effectiveGlobal = governanceService.effectivePolicy(null);
        List<RecruitmentAiGovernanceDtos.EvalSuiteView> suites = evaluationService.suites().stream()
                .map(suite -> suiteView(suite, effectiveGlobal)).toList();
        RecruitmentAiGovernanceService.CostUsage usage = governanceService.costUsage(null);
        long pendingMatches = matchMapper.selectCount(new LambdaQueryWrapper<JobMatchEvaluation>()
                .eq(JobMatchEvaluation::getHumanReviewRequired, 1)
                .eq(JobMatchEvaluation::getHumanReviewStatus, "PENDING"));
        long pendingReports = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getHumanReviewRequired, 1).eq(Report::getHumanReviewStatus, "PENDING"));
        List<RecruitmentAiGovernanceDtos.GovernanceEventView> events = governanceService.recentEvents(20).stream()
                .map(this::eventView).toList();
        return new RecruitmentAiGovernanceDtos.Overview(LocalDateTime.now(), readiness(global, suites),
                policyView(global), tenants.stream().map(this::policyView).toList(), suites,
                new RecruitmentAiGovernanceDtos.CostUsageView(usage.todayUsd(), usage.monthUsd(),
                        global.getDailyCostLimitUsd(), global.getMonthlyCostLimitUsd()),
                pendingMatches, pendingReports, events);
    }

    @Transactional
    public RecruitmentAiGovernanceDtos.PolicyView updateGlobal(RecruitmentAiGovernanceDtos.PolicyUpdate request) {
        RecruitmentAiPolicy current = requireGlobal();
        RecruitmentAiPolicy updated = update(current, request);
        auditService.success("AI_GOVERNANCE", "GLOBAL_POLICY_UPDATED", "RECRUITMENT_AI_POLICY", updated.getId(),
                null, "更新招聘 AI 全局治理策略");
        governanceService.recordAdministrativeEvent(null, updated.getId(), "POLICY", "POLICY_UPDATED", "全局治理策略已更新");
        return policyView(updated);
    }

    @Transactional
    public RecruitmentAiGovernanceDtos.PolicyView updateTenant(Long companyId,
                                                                RecruitmentAiGovernanceDtos.PolicyUpdate request) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || !Integer.valueOf(1).equals(company.getStatus())) {
            throw BusinessException.notFound("企业不存在或已停用");
        }
        RecruitmentAiPolicy current = policyMapper.selectByCompanyId(companyId);
        RecruitmentAiPolicy updated;
        if (current == null) {
            if (request.version() != 0) throw BusinessException.conflict("企业治理策略版本已变化，请刷新后重试");
            current = new RecruitmentAiPolicy();
            current.setScopeKey("COMPANY:" + companyId);
            current.setCompanyId(companyId);
            current.setEmergencyStop(0);
            current.setVersion(0);
            apply(current, request);
            current.setUpdatedBy(currentUser.id());
            current.setCreatedAt(LocalDateTime.now());
            current.setUpdatedAt(LocalDateTime.now());
            policyMapper.insert(current);
            updated = current;
        } else {
            updated = update(current, request);
        }
        auditService.success("AI_GOVERNANCE", "TENANT_POLICY_UPDATED", "RECRUITMENT_AI_POLICY", updated.getId(),
                companyId, "更新企业招聘 AI 治理策略");
        governanceService.recordAdministrativeEvent(companyId, updated.getId(), "POLICY", "TENANT_POLICY_UPDATED", "企业治理策略已更新");
        return policyView(updated);
    }

    @Transactional
    public void resetTenant(Long companyId) {
        RecruitmentAiPolicy current = policyMapper.selectByCompanyId(companyId);
        if (current == null) return;
        policyMapper.deleteById(current.getId());
        auditService.success("AI_GOVERNANCE", "TENANT_POLICY_RESET", "RECRUITMENT_AI_POLICY", current.getId(),
                companyId, "企业招聘 AI 策略恢复继承全局策略");
        governanceService.recordAdministrativeEvent(companyId, null, "POLICY", "TENANT_POLICY_RESET",
                "企业治理策略已恢复继承全局策略");
    }

    @Transactional
    public RecruitmentAiGovernanceDtos.PolicyView emergency(RecruitmentAiGovernanceDtos.EmergencyStopRequest request) {
        if (!Boolean.TRUE.equals(request.confirm())) throw BusinessException.badRequest("必须明确确认紧急停用操作");
        if (Boolean.TRUE.equals(request.enabled()) && !StringUtils.hasText(request.reason())) {
            throw BusinessException.badRequest("开启紧急停用时必须填写原因");
        }
        RecruitmentAiPolicy current = requireGlobal();
        if (!Integer.valueOf(request.version()).equals(current.getVersion())) {
            throw BusinessException.conflict("全局治理策略版本已变化，请刷新后重试");
        }
        int updated = policyMapper.update(null, new LambdaUpdateWrapper<RecruitmentAiPolicy>()
                .eq(RecruitmentAiPolicy::getId, current.getId())
                .eq(RecruitmentAiPolicy::getVersion, current.getVersion())
                .set(RecruitmentAiPolicy::getEmergencyStop, Boolean.TRUE.equals(request.enabled()) ? 1 : 0)
                .set(RecruitmentAiPolicy::getEmergencyReason, trim(request.reason(), 500))
                .set(RecruitmentAiPolicy::getUpdatedBy, currentUser.id())
                .set(RecruitmentAiPolicy::getVersion, current.getVersion() + 1)
                .set(RecruitmentAiPolicy::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) throw BusinessException.conflict("全局治理策略版本已变化，请刷新后重试");
        RecruitmentAiPolicy result = requireGlobal();
        String action = Boolean.TRUE.equals(request.enabled()) ? "EMERGENCY_STOP_ENABLED" : "EMERGENCY_STOP_DISABLED";
        auditService.success("AI_GOVERNANCE", action, "RECRUITMENT_AI_POLICY", result.getId(), null,
                Boolean.TRUE.equals(request.enabled()) ? "开启招聘 AI 紧急停用" : "解除招聘 AI 紧急停用");
        governanceService.recordAdministrativeEvent(null, result.getId(), "EMERGENCY_STOP", action,
                Boolean.TRUE.equals(request.enabled()) ? "招聘 AI 已紧急停用" : "招聘 AI 紧急停用已解除");
        return policyView(result);
    }

    public RecruitmentAiEvalRun startRun(Long suiteId) {
        RecruitmentAiEvalRun run = evaluationService.start(suiteId, currentUser.id());
        auditService.success("AI_GOVERNANCE", "EVALUATION_RUN_STARTED", "RECRUITMENT_AI_EVAL_RUN", run.getId(),
                null, "启动招聘 AI 模型评测");
        return run;
    }

    public RecruitmentAiGovernanceDtos.EvalCaseView createCase(Long suiteId,
                                                               RecruitmentAiGovernanceDtos.EvalCaseRequest request) {
        RecruitmentAiEvalCase item = evaluationService.createCase(suiteId, request, currentUser.id());
        auditService.success("AI_GOVERNANCE", "EVALUATION_CASE_CREATED", "RECRUITMENT_AI_EVAL_CASE", item.getId(),
                null, "新增招聘 AI 合成评测用例");
        return caseView(item);
    }

    public List<RecruitmentAiGovernanceDtos.EvalCaseView> cases(Long suiteId) {
        return evaluationService.cases(suiteId).stream().map(this::caseView).toList();
    }

    public RecruitmentAiGovernanceDtos.EvalRunView run(Long id) {
        return runView(evaluationService.run(id));
    }

    private RecruitmentAiPolicy update(RecruitmentAiPolicy current,
                                       RecruitmentAiGovernanceDtos.PolicyUpdate request) {
        if (!Integer.valueOf(request.version()).equals(current.getVersion())) {
            throw BusinessException.conflict("治理策略版本已变化，请刷新后重试");
        }
        RecruitmentAiPolicy values = new RecruitmentAiPolicy();
        apply(values, request);
        int nextVersion = current.getVersion() + 1;
        int updated = policyMapper.update(null, new LambdaUpdateWrapper<RecruitmentAiPolicy>()
                .eq(RecruitmentAiPolicy::getId, current.getId())
                .eq(RecruitmentAiPolicy::getVersion, current.getVersion())
                .set(RecruitmentAiPolicy::getAiEnabled, values.getAiEnabled())
                .set(RecruitmentAiPolicy::getEvaluationGateRequired, values.getEvaluationGateRequired())
                .set(RecruitmentAiPolicy::getEvaluationValidDays, values.getEvaluationValidDays())
                .set(RecruitmentAiPolicy::getMinimumPassRate, values.getMinimumPassRate())
                .set(RecruitmentAiPolicy::getMaximumScoreDrift, values.getMaximumScoreDrift())
                .set(RecruitmentAiPolicy::getMaximumFairnessGap, values.getMaximumFairnessGap())
                .set(RecruitmentAiPolicy::getHumanReviewMode, values.getHumanReviewMode())
                .set(RecruitmentAiPolicy::getAdverseScoreThreshold, values.getAdverseScoreThreshold())
                .set(RecruitmentAiPolicy::getSensitiveDataMode, values.getSensitiveDataMode())
                .set(RecruitmentAiPolicy::getDailyCostLimitUsd, values.getDailyCostLimitUsd())
                .set(RecruitmentAiPolicy::getMonthlyCostLimitUsd, values.getMonthlyCostLimitUsd())
                .set(RecruitmentAiPolicy::getInputCostPerMillionUsd, values.getInputCostPerMillionUsd())
                .set(RecruitmentAiPolicy::getOutputCostPerMillionUsd, values.getOutputCostPerMillionUsd())
                .set(RecruitmentAiPolicy::getPerRequestTokenLimit, values.getPerRequestTokenLimit())
                .set(RecruitmentAiPolicy::getUpdatedBy, currentUser.id())
                .set(RecruitmentAiPolicy::getVersion, nextVersion)
                .set(RecruitmentAiPolicy::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) throw BusinessException.conflict("治理策略版本已变化，请刷新后重试");
        return policyMapper.selectById(current.getId());
    }

    private void apply(RecruitmentAiPolicy target, RecruitmentAiGovernanceDtos.PolicyUpdate source) {
        if (Boolean.TRUE.equals(source.aiEnabled()) && !Boolean.TRUE.equals(source.evaluationGateRequired())) {
            throw BusinessException.badRequest("启用招聘 AI 时必须开启模型评测回归门禁");
        }
        String reviewMode = source.humanReviewMode().trim().toUpperCase(Locale.ROOT);
        String sensitiveMode = source.sensitiveDataMode().trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "ADVERSE_ONLY", "LOW_CONFIDENCE").contains(reviewMode)) {
            throw BusinessException.badRequest("人工复核策略不合法");
        }
        if (!List.of("REDACT", "BLOCK_ON_DETECTION").contains(sensitiveMode)) {
            throw BusinessException.badRequest("敏感字段策略不合法");
        }
        target.setAiEnabled(Boolean.TRUE.equals(source.aiEnabled()) ? 1 : 0);
        target.setEvaluationGateRequired(Boolean.TRUE.equals(source.evaluationGateRequired()) ? 1 : 0);
        target.setEvaluationValidDays(source.evaluationValidDays());
        target.setMinimumPassRate(source.minimumPassRate());
        target.setMaximumScoreDrift(source.maximumScoreDrift());
        target.setMaximumFairnessGap(source.maximumFairnessGap());
        target.setHumanReviewMode(reviewMode);
        target.setAdverseScoreThreshold(source.adverseScoreThreshold());
        target.setSensitiveDataMode(sensitiveMode);
        target.setDailyCostLimitUsd(source.dailyCostLimitUsd());
        target.setMonthlyCostLimitUsd(source.monthlyCostLimitUsd());
        target.setInputCostPerMillionUsd(source.inputCostPerMillionUsd());
        target.setOutputCostPerMillionUsd(source.outputCostPerMillionUsd());
        target.setPerRequestTokenLimit(source.perRequestTokenLimit());
    }

    private RecruitmentAiGovernanceDtos.EvalSuiteView suiteView(
            RecruitmentAiEvalSuite suite, RecruitmentAiGovernanceService.EffectivePolicy policy) {
        long count = caseMapper.selectCount(new LambdaQueryWrapper<RecruitmentAiEvalCase>()
                .eq(RecruitmentAiEvalCase::getSuiteId, suite.getId()).eq(RecruitmentAiEvalCase::getEnabled, 1));
        RecruitmentAiEvaluationService.GateStatus gate = evaluationService.currentGateStatus(suite, policy);
        return new RecruitmentAiGovernanceDtos.EvalSuiteView(suite.getId(), suite.getSuiteCode(), suite.getName(),
                suite.getEvaluationType(), suite.getPromptCode(), suite.getDescription(), count,
                gate.ready(), gate.provider(), gate.model(), gate.promptVersion(),
                runView(evaluationService.latestRun(suite.getId())));
    }

    private RecruitmentAiGovernanceDtos.EvalCaseView caseView(RecruitmentAiEvalCase item) {
        return new RecruitmentAiGovernanceDtos.EvalCaseView(item.getId(), item.getSuiteId(), item.getCaseCode(),
                item.getName(), item.getCohortCode(), item.getPairKey(), tree(item.getInputJson()),
                item.getExpectedScoreMin(), item.getExpectedScoreMax(), item.getBaselineScore(),
                terms(item.getRequiredTerms()), terms(item.getForbiddenTerms()), Integer.valueOf(1).equals(item.getEnabled()),
                item.getUpdatedAt());
    }

    private RecruitmentAiGovernanceDtos.EvalRunView runView(RecruitmentAiEvalRun run) {
        if (run == null) return null;
        return new RecruitmentAiGovernanceDtos.EvalRunView(run.getId(), run.getSuiteId(), run.getStatus(),
                run.getProvider(), run.getModel(), run.getPromptCode(), run.getPromptVersion(), run.getCaseCount(),
                run.getPassedCaseCount(), run.getPassRate(), run.getMaximumScoreDrift(), run.getMaximumFairnessGap(),
                run.getFailureSummary(), run.getStartedBy(), run.getStartedAt(), run.getFinishedAt());
    }

    private RecruitmentAiGovernanceDtos.PolicyView policyView(RecruitmentAiPolicy item) {
        return new RecruitmentAiGovernanceDtos.PolicyView(item.getId(), item.getCompanyId(),
                Integer.valueOf(1).equals(item.getAiEnabled()), Integer.valueOf(1).equals(item.getEmergencyStop()),
                item.getEmergencyReason(), Integer.valueOf(1).equals(item.getEvaluationGateRequired()),
                item.getEvaluationValidDays(), item.getMinimumPassRate(), item.getMaximumScoreDrift(),
                item.getMaximumFairnessGap(), item.getHumanReviewMode(), item.getAdverseScoreThreshold(),
                item.getSensitiveDataMode(), item.getDailyCostLimitUsd(), item.getMonthlyCostLimitUsd(),
                item.getInputCostPerMillionUsd(), item.getOutputCostPerMillionUsd(), item.getPerRequestTokenLimit(),
                item.getVersion(), item.getUpdatedAt());
    }

    private RecruitmentAiGovernanceDtos.GovernanceEventView eventView(RecruitmentAiGovernanceEvent item) {
        return new RecruitmentAiGovernanceDtos.GovernanceEventView(item.getId(), item.getCompanyId(),
                item.getEventType(), item.getGenerationType(), item.getDecision(), item.getReasonCode(),
                item.getSummary(), item.getCreatedAt());
    }

    private String readiness(RecruitmentAiPolicy global, List<RecruitmentAiGovernanceDtos.EvalSuiteView> suites) {
        if (!Integer.valueOf(1).equals(global.getAiEnabled()) || Integer.valueOf(1).equals(global.getEmergencyStop())) {
            return "STOPPED";
        }
        return suites.stream().allMatch(RecruitmentAiGovernanceDtos.EvalSuiteView::gateReady)
                ? "READY" : "BLOCKED";
    }

    private RecruitmentAiPolicy requireGlobal() {
        RecruitmentAiPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<RecruitmentAiPolicy>()
                .eq(RecruitmentAiPolicy::getScopeKey, "GLOBAL").last("LIMIT 1"));
        if (policy == null) throw new IllegalStateException("招聘 AI 全局治理策略不存在");
        return policy;
    }

    private JsonNode tree(String value) {
        try { return objectMapper.readTree(value == null ? "{}" : value); }
        catch (JsonProcessingException exception) { return objectMapper.createObjectNode(); }
    }
    private List<String> terms(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException exception) { return List.of(); }
    }
    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
