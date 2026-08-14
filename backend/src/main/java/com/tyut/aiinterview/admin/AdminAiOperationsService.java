package com.tyut.aiinterview.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiGenerationRecord;
import com.tyut.aiinterview.domain.AiPromptVersion;
import com.tyut.aiinterview.domain.AiProviderConfig;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.mapper.AdminAiOpsCallRow;
import com.tyut.aiinterview.mapper.AdminAiOpsGenerationSummaryRow;
import com.tyut.aiinterview.mapper.AdminAiOpsTaskRow;
import com.tyut.aiinterview.mapper.AdminAiOpsTaskSummaryRow;
import com.tyut.aiinterview.mapper.AdminAiOperationsMapper;
import com.tyut.aiinterview.mapper.AiGenerationRecordMapper;
import com.tyut.aiinterview.mapper.AiPromptVersionMapper;
import com.tyut.aiinterview.mapper.AiProviderConfigMapper;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiOperationsService {
    private static final int RECENT_LIMIT = 8;
    private static final Set<String> ADMIN_RETRY_TYPES = Set.of(
            AiTaskService.FOLLOW_UP, AiTaskService.OPENING, AiTaskService.AUTO_EVALUATION,
            AiTaskService.FREE_INTERVIEW_ANALYSIS, AiTaskService.FREE_INTERVIEW_FOLLOW_UP,
            AiTaskService.FREE_INTERVIEW_REPORT, AiTaskService.RESUME_PARSE, AiTaskService.JOB_MATCH);

    private final AdminAiOperationsMapper mapper;
    private final AiTaskMapper taskMapper;
    private final AiGenerationRecordMapper generationMapper;
    private final AiProviderConfigMapper providerMapper;
    private final AiPromptVersionMapper promptMapper;
    private final AiTaskService taskService;
    private final OperationAuditService auditService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public AdminAiOperationsService(AdminAiOperationsMapper mapper, AiTaskMapper taskMapper,
                                    AiGenerationRecordMapper generationMapper,
                                    AiProviderConfigMapper providerMapper,
                                    AiPromptVersionMapper promptMapper,
                                    AiTaskService taskService,
                                    OperationAuditService auditService,
                                    CurrentUser currentUser,
                                    ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.taskMapper = taskMapper;
        this.generationMapper = generationMapper;
        this.providerMapper = providerMapper;
        this.promptMapper = promptMapper;
        this.taskService = taskService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    public AdminAiOperationsDtos.Overview overview() {
        requireAdmin();
        AdminAiOpsGenerationSummaryRow generation = mapper.selectGenerationSummary();
        AdminAiOpsTaskSummaryRow tasks = mapper.selectTaskSummary();
        List<AiProviderConfig> providerConfigs = providerMapper.selectList(
                new LambdaQueryWrapper<AiProviderConfig>().orderByAsc(AiProviderConfig::getKind).orderByAsc(AiProviderConfig::getId));
        List<AdminAiOperationsDtos.ProviderView> providers = providerConfigs.stream()
                .filter(item -> !"virtual-human".equals(item.getKind())
                        || "open-talking-virtual-human".equals(item.getCode()))
                .map(this::providerView).toList();
        Map<String, AiPromptVersion> activePrompts = promptMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiPromptVersion::getPromptCode, Function.identity(),
                        (left, right) -> version(left) >= version(right) ? left : right));
        List<AdminAiOperationsDtos.PromptView> prompts = activePrompts.values().stream()
                .sorted(Comparator.comparing(AiPromptVersion::getPromptCode, Comparator.nullsLast(String::compareTo)))
                .map(this::promptView).toList();
        List<AdminAiOperationsDtos.CallView> calls = mapper.selectRecentCalls(RECENT_LIMIT).stream()
                .map(this::callView).toList();
        List<AdminAiOperationsDtos.TaskView> recentTasks = mapper.selectRecentTasks(RECENT_LIMIT).stream()
                .map(row -> taskView(row, businessFrom(row.getInterviewId(), row.getTaskType(), row.getId())))
                .toList();
        return new AdminAiOperationsDtos.Overview(LocalDateTime.now(),
                new AdminAiOperationsDtos.AiSummary(value(generation == null ? null : generation.getTotal()),
                        value(generation == null ? null : generation.getSuccess()),
                        value(generation == null ? null : generation.getFailed()),
                        value(generation == null ? null : generation.getRunning()),
                        value(generation == null ? null : generation.getAverageLatencyMs()),
                        value(generation == null ? null : generation.getTotalTokens()), "最近 24 小时"),
                new AdminAiOperationsDtos.TaskSummary(value(tasks == null ? null : tasks.getPending()),
                        value(tasks == null ? null : tasks.getRunning()),
                        value(tasks == null ? null : tasks.getFailed()),
                        value(tasks == null ? null : tasks.getBacklog()),
                        value(tasks == null ? null : tasks.getReportBacklog()),
                        tasks == null ? null : tasks.getOldestPendingAt()),
                providers, prompts, calls, recentTasks);
    }

    public AdminAiOperationsDtos.Trace trace(Long generationId) {
        requireAdmin();
        AiGenerationRecord generation = generationMapper.selectById(generationId);
        if (generation == null) throw BusinessException.notFound("AI 调用记录不存在");
        AiTask task = generation.getTaskId() == null ? null : taskMapper.selectById(generation.getTaskId());
        AdminAiOperationsDtos.BusinessRef business = businessFrom(generation, task);
        AdminAiOperationsDtos.TaskView taskView = task == null ? null : taskView(task, business);
        AiProviderConfig provider = provider(generation.getProvider());
        AiPromptVersion prompt = prompt(generation.getPromptCode(), generation.getPromptVersionNo());
        AdminAiOperationsDtos.GenerationView generationView = new AdminAiOperationsDtos.GenerationView(
                generation.getId(), generation.getRequestId(), generation.getStatus(), generation.getGenerationType(),
                generation.getLatencyMs(), generation.getInputChars(), generation.getOutputChars(),
                generation.getTotalTokens(), generation.getHttpStatus(), generation.getStartedAt(), generation.getFinishedAt());
        AdminAiOperationsDtos.ProviderRef providerRef = provider == null
                ? new AdminAiOperationsDtos.ProviderRef(generation.getProvider(), "未找到 Provider 配置", null, generation.getModel())
                : new AdminAiOperationsDtos.ProviderRef(provider.getCode(), provider.getName(), provider.getKind(), generation.getModel());
        AdminAiOperationsDtos.PromptRef promptRef = prompt == null
                ? new AdminAiOperationsDtos.PromptRef(generation.getPromptCode(), "未找到 Prompt 版本", generation.getPromptVersionNo(), null, false)
                : new AdminAiOperationsDtos.PromptRef(prompt.getPromptCode(), prompt.getPromptName(), prompt.getVersionNo(), prompt.getCategory(), truthy(prompt.getActive()));
        return new AdminAiOperationsDtos.Trace(business, taskView, generationView, providerRef, promptRef,
                new AdminAiOperationsDtos.ResultRef(business == null ? "AI_RESULT" : business.type(),
                        business == null ? "业务结果由原业务页面展示" : "返回关联业务页面查看结果",
                        business == null ? null : business.path()));
    }

    @Transactional
    public AdminAiOperationsDtos.TaskView retry(Long taskId, Boolean confirm) {
        requireAdmin();
        if (!Boolean.TRUE.equals(confirm)) {
            auditService.denied("AI_OPERATIONS", "TASK_RETRY", "AI_TASK", taskId, null,
                    "重试异步 AI 任务前需要明确确认");
            throw BusinessException.badRequest("重试异步 AI 任务前需要明确确认");
        }
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) throw BusinessException.notFound("AI 任务不存在");
        if (!ADMIN_RETRY_TYPES.contains(task.getTaskType())) {
            auditService.denied("AI_OPERATIONS", "TASK_RETRY", "AI_TASK", taskId, null,
                    "该任务类型不支持平台受控重试");
            throw BusinessException.badRequest("该任务类型不支持平台受控重试");
        }
        boolean noOp = !"FAILED".equals(task.getStatus());
        AdminAiOperationsDtos.BusinessRef business = businessFrom(task);
        AiTask result;
        try {
            result = taskService.retryAdminAiTask(taskId);
        } catch (BusinessException exception) {
            auditService.denied("AI_OPERATIONS", "TASK_RETRY", "AI_TASK", taskId, null,
                    "任务重试被拒绝，仅允许技术失败任务");
            throw exception;
        }
        auditService.success("AI_OPERATIONS", noOp ? "TASK_RETRY_NOOP" : "TASK_RETRY",
                "AI_TASK", taskId, null, noOp ? "任务未处于失败状态，保持原状态" : "重置异步 AI 任务并保留原去重键");
        return taskView(result, business);
    }

    private AdminAiOperationsDtos.TaskView taskView(AdminAiOpsTaskRow row,
                                                      AdminAiOperationsDtos.BusinessRef business) {
        return new AdminAiOperationsDtos.TaskView(row.getId(), row.getTaskType(), row.getStatus(),
                row.getAttempts(), row.getMaxAttempts(), row.getScheduledAt(), row.getStartedAt(),
                row.getFinishedAt(), row.getInterviewId(), row.getAnswerId(), row.getGenerationRequestId(),
                row.getProvider(), row.getModel(), row.getPromptCode(), row.getPromptVersion(),
                retryable(row.getTaskType(), row.getStatus()), failureSummary(row.getStatus()), business);
    }

    private AdminAiOperationsDtos.TaskView taskView(AiTask task, AdminAiOperationsDtos.BusinessRef business) {
        AiGenerationRecord generation = task.getId() == null ? null : generationMapper.selectOne(
                new LambdaQueryWrapper<AiGenerationRecord>().eq(AiGenerationRecord::getTaskId, task.getId())
                        .orderByDesc(AiGenerationRecord::getId).last("LIMIT 1"));
        return new AdminAiOperationsDtos.TaskView(task.getId(), task.getTaskType(), task.getStatus(),
                task.getAttempts(), task.getMaxAttempts(), task.getScheduledAt(), task.getStartedAt(),
                task.getFinishedAt(), task.getInterviewId(), task.getAnswerId(),
                generation == null ? null : generation.getRequestId(),
                generation == null ? null : generation.getProvider(), generation == null ? null : generation.getModel(),
                generation == null ? null : generation.getPromptCode(), generation == null ? null : generation.getPromptVersionNo(),
                retryable(task.getTaskType(), task.getStatus()), failureSummary(task.getStatus()), business);
    }

    private AdminAiOperationsDtos.CallView callView(AdminAiOpsCallRow row) {
        return new AdminAiOperationsDtos.CallView(row.getId(), row.getRequestId(), row.getTaskId(), row.getInterviewId(),
                row.getFreeInterviewSessionId(), row.getGenerationType(), row.getPromptCode(), row.getPromptVersion(),
                row.getProvider(), row.getModel(), row.getStatus(), row.getLatencyMs(), row.getInputChars(),
                row.getOutputChars(), row.getTotalTokens(), row.getHttpStatus(), safeError(row.getStatus()),
                row.getStartedAt(), row.getFinishedAt());
    }

    private AdminAiOperationsDtos.ProviderView providerView(AiProviderConfig config) {
        boolean enabled = truthy(config.getEnabled());
        boolean configured = "speech".equals(config.getKind())
                || ("virtual-human".equals(config.getKind())
                ? StringUtils.hasText(config.getBaseUrl()) && StringUtils.hasText(config.getAvatarModel())
                : StringUtils.hasText(config.getBaseUrl()) && StringUtils.hasText(config.getChatModel()));
        String testState = config.getLastTestState();
        String state = !enabled ? "DISABLED" : !configured ? "ATTENTION"
                : "SUCCESS".equals(testState) ? "UP"
                : "FAILED".equals(testState) || "TIMEOUT".equals(testState) ? "DOWN" : "CONFIGURED";
        String label = !enabled ? "已停用" : !configured ? "需要补充配置"
                : "SUCCESS".equals(testState) ? "测试通过"
                : "TIMEOUT".equals(testState) ? "测试超时"
                : "FAILED".equals(testState) ? "测试失败" : "已配置，待测试";
        String model = "llm".equals(config.getKind()) ? config.getChatModel()
                : "virtual-human".equals(config.getKind()) ? config.getAvatarModel() : config.getVoiceModel();
        return new AdminAiOperationsDtos.ProviderView(config.getId(), config.getName(), config.getCode(),
                config.getKind(), model, state, label, enabled, truthy(config.getTextDefault()), truthy(config.getVoiceDefault()),
                config.getLastTestState(), config.getLastTestStatusCode(), config.getLastTestLatencyMs(),
                config.getLastTestMessage(), config.getLastTestedAt());
    }

    private AdminAiOperationsDtos.PromptView promptView(AiPromptVersion version) {
        return new AdminAiOperationsDtos.PromptView(version.getPromptCode(), version.getPromptName(), version.getCategory(),
                version.getVersionNo(), truthy(version.getActive()), version.getActivatedAt());
    }

    private AiProviderConfig provider(String code) {
        if (!StringUtils.hasText(code)) return null;
        return providerMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getCode, code).last("LIMIT 1"));
    }

    private AiPromptVersion prompt(String code, Integer version) {
        if (!StringUtils.hasText(code) || version == null) return null;
        return promptMapper.selectOne(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptCode, code).eq(AiPromptVersion::getVersionNo, version).last("LIMIT 1"));
    }

    private AdminAiOperationsDtos.BusinessRef businessFrom(AiGenerationRecord generation, AiTask task) {
        if (generation.getInterviewId() != null) return interviewRef(generation.getInterviewId());
        if (generation.getFreeInterviewSessionId() != null) return new AdminAiOperationsDtos.BusinessRef(
                "FREE_INTERVIEW_SESSION", generation.getFreeInterviewSessionId(), "自由面试会话 #" + generation.getFreeInterviewSessionId(), null);
        return businessFrom(task);
    }

    private AdminAiOperationsDtos.BusinessRef businessFrom(AiTask task) {
        if (task == null) return null;
        if (task.getInterviewId() != null) return interviewRef(task.getInterviewId());
        JsonNode payload = tree(task.getInputPayload());
        if (payload.hasNonNull("applicationId")) {
            Long id = payload.get("applicationId").asLong();
            return new AdminAiOperationsDtos.BusinessRef("APPLICATION", id, "招聘申请 #" + id,
                    "/admin/recruitment/applications/" + id);
        }
        if (payload.hasNonNull("resumeId")) {
            Long id = payload.get("resumeId").asLong();
            return new AdminAiOperationsDtos.BusinessRef("RESUME", id, "候选人简历 #" + id, null);
        }
        if (payload.hasNonNull("sessionId")) {
            Long id = payload.get("sessionId").asLong();
            return new AdminAiOperationsDtos.BusinessRef("FREE_INTERVIEW_SESSION", id, "自由面试会话 #" + id, null);
        }
        return null;
    }

    private AdminAiOperationsDtos.BusinessRef businessFrom(Long interviewId, String taskType, Long taskId) {
        if (interviewId != null) return interviewRef(interviewId);
        return new AdminAiOperationsDtos.BusinessRef("AI_TASK", taskId, "异步 AI 任务 #" + taskId, null);
    }

    private AdminAiOperationsDtos.BusinessRef interviewRef(Long id) {
        return new AdminAiOperationsDtos.BusinessRef("INTERVIEW", id, "面试 #" + id,
                "/admin/interviews/" + id + "/review");
    }

    private JsonNode tree(String payload) {
        if (!StringUtils.hasText(payload)) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean retryable(String type, String status) {
        return "FAILED".equals(status) && ADMIN_RETRY_TYPES.contains(type);
    }

    private String failureSummary(String status) {
        return "FAILED".equals(status) ? "任务失败，可按需执行受控重试" : null;
    }

    private String safeError(String status) {
        return "FAILED".equals(status) ? "技术任务失败，详情已脱敏" : null;
    }

    private int version(AiPromptVersion version) {
        return version.getVersionNo() == null ? 0 : version.getVersionNo();
    }

    private boolean truthy(Integer value) {
        return value != null && value == 1;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅超级管理员可查看 AI 与运维数据");
    }
}
