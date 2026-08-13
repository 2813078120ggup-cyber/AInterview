package com.tyut.aiinterview.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobApplicationStatusHistory;
import com.tyut.aiinterview.mapper.AdminRecruitmentApplicationRow;
import com.tyut.aiinterview.mapper.AdminRecruitmentFunnelRow;
import com.tyut.aiinterview.mapper.AdminRecruitmentMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.recruitment.ApplicationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminRecruitmentService {
    private static final int DEFAULT_STALE_DAYS = 3;
    private static final int MAX_STALE_DAYS = 365;
    private static final Set<String> TERMINAL_STATUSES = Set.of("REJECTED", "HIRED");

    private final AdminRecruitmentMapper mapper;
    private final JobApplicationStatusHistoryMapper historyMapper;
    private final JobApplicationMapper applicationMapper;
    private final com.tyut.aiinterview.mapper.AiTaskMapper taskMapper;
    private final AiTaskService taskService;
    private final OperationAuditService auditService;
    private final ObjectMapper objectMapper;

    public AdminRecruitmentService(AdminRecruitmentMapper mapper,
                                   JobApplicationStatusHistoryMapper historyMapper,
                                   JobApplicationMapper applicationMapper,
                                   com.tyut.aiinterview.mapper.AiTaskMapper taskMapper,
                                   AiTaskService taskService,
                                   OperationAuditService auditService,
                                   ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.historyMapper = historyMapper;
        this.applicationMapper = applicationMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public PageResult<AdminRecruitmentDtos.ApplicationView> page(AdminRecruitmentDtos.Query query) {
        Filter filter = Filter.from(query);
        List<AdminRecruitmentDtos.ApplicationView> records = mapper.selectPage(
                        filter.companyId(), filter.positionId(), filter.status(), filter.companyKeyword(),
                        filter.positionKeyword(), filter.keyword(), filter.fromTime(), filter.toTime(),
                        filter.staleOnly() ? 1 : 0, filter.staleBefore(), filter.offset(), filter.pageSize())
                .stream().map(row -> toView(row, filter.staleBefore(), filter.staleDays())).toList();
        long total = mapper.count(filter.companyId(), filter.positionId(), filter.status(), filter.companyKeyword(),
                filter.positionKeyword(), filter.keyword(), filter.fromTime(), filter.toTime(),
                filter.staleOnly() ? 1 : 0, filter.staleBefore());
        return PageResult.of(records, total, filter.pageNo(), filter.pageSize());
    }

    public AdminRecruitmentDtos.Summary summary(AdminRecruitmentDtos.Query query) {
        Filter filter = Filter.from(query);
        Map<String, Long> counts = mapper.selectFunnel(filter.companyId(), filter.positionId(), filter.status(), filter.companyKeyword(),
                        filter.positionKeyword(), filter.keyword(), filter.fromTime(), filter.toTime(),
                        filter.staleOnly() ? 1 : 0, filter.staleBefore()).stream()
                .collect(Collectors.toMap(AdminRecruitmentFunnelRow::getStatus, row -> value(row.getItemCount()), (left, right) -> right));
        List<AdminRecruitmentDtos.FunnelStage> funnel = EnumSet.allOf(ApplicationStatus.class).stream()
                .map(status -> new AdminRecruitmentDtos.FunnelStage(status.name(), status.label(),
                        counts.getOrDefault(status.name(), 0L), status.terminal()))
                .toList();
        long staleCount = mapper.count(filter.companyId(), filter.positionId(), filter.status(), filter.companyKeyword(),
                filter.positionKeyword(), filter.keyword(), filter.fromTime(), filter.toTime(), 1, filter.staleBefore());
        return new AdminRecruitmentDtos.Summary(LocalDateTime.now(), filter.staleDays(), staleCount, funnel);
    }

    public AdminRecruitmentDtos.Detail detail(Long id) {
        AdminRecruitmentApplicationRow row = mapper.selectById(id);
        if (row == null) throw BusinessException.notFound("申请不存在");
        LocalDateTime staleBefore = LocalDateTime.now().minusDays(DEFAULT_STALE_DAYS);
        List<AdminRecruitmentDtos.StatusEvent> history = historyMapper.selectList(new LambdaQueryWrapper<JobApplicationStatusHistory>()
                        .eq(JobApplicationStatusHistory::getApplicationId, id)
                        .orderByAsc(JobApplicationStatusHistory::getCreatedAt)
                        .orderByAsc(JobApplicationStatusHistory::getId))
                .stream().map(item -> new AdminRecruitmentDtos.StatusEvent(item.getId(), item.getFromStatus(),
                        item.getToStatus(), item.getOperatorId(), item.getCreatedAt())).toList();
        return new AdminRecruitmentDtos.Detail(toView(row, staleBefore, DEFAULT_STALE_DAYS), history);
    }

    @Transactional
    public AdminRecruitmentDtos.TaskView retry(Long taskId, Boolean confirm) {
        if (!Boolean.TRUE.equals(confirm)) {
            auditService.denied("ADMIN_RECRUITMENT", "TASK_RETRY", "AI_TASK", taskId, null,
                    "重试技术任务前需要明确确认");
            throw BusinessException.badRequest("重试技术任务前需要明确确认");
        }
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) throw BusinessException.notFound("AI 任务不存在");
        if (!Set.of(AiTaskService.JOB_MATCH, AiTaskService.AUTO_EVALUATION).contains(task.getTaskType())) {
            auditService.denied("ADMIN_RECRUITMENT", "TASK_RETRY", "AI_TASK", taskId, null,
                    "拒绝非招聘运营任务重试");
            throw BusinessException.badRequest("该任务类型不支持招聘运营重试");
        }
        Long applicationId = applicationId(task);
        if (applicationId == null && task.getInterviewId() != null) {
            JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getInterviewId, task.getInterviewId()).last("LIMIT 1"));
            applicationId = application == null ? null : application.getId();
        }
        if (applicationId == null || applicationMapper.selectById(applicationId) == null) {
            auditService.denied("ADMIN_RECRUITMENT", "TASK_RETRY", "AI_TASK", taskId, null,
                    "拒绝未关联招聘申请的任务重试");
            throw BusinessException.notFound("招聘申请或任务关联不存在");
        }
        boolean noOp = !"FAILED".equals(task.getStatus());
        AiTask result;
        try {
            result = taskService.retryAdminRecruitmentTask(taskId);
        } catch (BusinessException exception) {
            auditService.denied("ADMIN_RECRUITMENT", "TASK_RETRY", "AI_TASK", taskId, null,
                    "任务重试被拒绝，仅允许技术失败任务");
            throw exception;
        }
        auditService.success("ADMIN_RECRUITMENT", noOp ? "TASK_RETRY_NOOP" : "TASK_RETRY",
                "AI_TASK", taskId, null, noOp ? "任务未处于失败状态，保持原状态" : "重置招聘技术任务并保留原去重键");
        return taskView(result, task.getTaskType().equals(AiTaskService.JOB_MATCH) ? "MATCH" : "REPORT");
    }

    private AdminRecruitmentDtos.ApplicationView toView(AdminRecruitmentApplicationRow row,
                                                         LocalDateTime staleBefore, int staleDays) {
        AdminRecruitmentDtos.TaskView matchTask = taskView(row.getMatchTaskId(), "MATCH", "JOB_MATCH",
                row.getMatchTaskStatus(), row.getMatchTaskAttempts(), row.getMatchTaskMaxAttempts(),
                row.getMatchTaskScheduledAt(), row.getMatchTaskStartedAt(), row.getMatchTaskFinishedAt());
        AdminRecruitmentDtos.TaskView reportTask = taskView(row.getReportTaskId(), "REPORT", "AUTO_EVALUATION",
                row.getReportTaskStatus(), row.getReportTaskAttempts(), row.getReportTaskMaxAttempts(),
                row.getReportTaskScheduledAt(), row.getReportTaskStartedAt(), row.getReportTaskFinishedAt());
        AdminRecruitmentDtos.InterviewView interview = row.getInterviewId() == null ? null
                : new AdminRecruitmentDtos.InterviewView(row.getInterviewId(), row.getInterviewType(), row.getInterviewStatus(),
                row.getInterviewScheduledAt(), row.getInterviewStartedAt(), row.getInterviewEndedAt(),
                row.getReportStatus(), row.getReportGeneratedAt(), row.getReportPublishedAt(), reportTask);
        boolean stale = row.getUpdatedAt() != null && row.getUpdatedAt().isBefore(staleBefore)
                && !TERMINAL_STATUSES.contains(row.getStatus());
        return new AdminRecruitmentDtos.ApplicationView(row.getId(), row.getApplicationNo(),
                new AdminRecruitmentDtos.Ref(row.getCompanyId(), row.getCompanyCode(), row.getCompanyName(), null),
                new AdminRecruitmentDtos.Ref(row.getPositionId(), null, row.getPositionName(), row.getPositionDepartment()),
                new AdminRecruitmentDtos.CandidateRef(row.getCandidateId(), row.getCandidateUsername(),
                        StringUtils.hasText(row.getCandidateRealName()) ? row.getCandidateRealName() : row.getCandidateUsername()),
                row.getStatus(), statusLabel(row.getStatus()), row.getMatchScore(), safeMatchStatus(row.getMatchStatus()),
                matchTask, interview, row.getSubmittedAt(), row.getUpdatedAt(), stale,
                nextAction(row, stale, staleDays));
    }

    private AdminRecruitmentDtos.TaskView taskView(Long id, String kind, String taskType, String status,
                                                    Integer attempts, Integer maxAttempts,
                                                    LocalDateTime scheduledAt, LocalDateTime startedAt,
                                                    LocalDateTime finishedAt) {
        if (id == null) return null;
        boolean retryable = "FAILED".equals(status);
        return new AdminRecruitmentDtos.TaskView(id, kind, taskType, status, attempts, maxAttempts,
                scheduledAt, startedAt, finishedAt, retryable,
                retryable ? "技术任务失败，可受控重试" : null);
    }

    private AdminRecruitmentDtos.TaskView taskView(AiTask task, String kind) {
        return taskView(task.getId(), kind, task.getTaskType(), task.getStatus(), task.getAttempts(), task.getMaxAttempts(),
                task.getScheduledAt(), task.getStartedAt(), task.getFinishedAt());
    }

    private String nextAction(AdminRecruitmentApplicationRow row, boolean stale, int staleDays) {
        if ("FAILED".equals(row.getMatchTaskStatus()) || "FAILED".equals(row.getMatchStatus())) return "检查匹配技术任务";
        if ("FAILED".equals(row.getReportTaskStatus()) || row.getInterviewStatus() != null && row.getInterviewStatus() == 7) {
            return "检查报告技术任务";
        }
        if (stale) return "申请超过 " + staleDays + " 天未推进";
        if (TERMINAL_STATUSES.contains(row.getStatus())) return "终态，仅供平台观测";
        return "由所属企业继续处理";
    }

    private String safeMatchStatus(String status) {
        return StringUtils.hasText(status) ? status : "NOT_STARTED";
    }

    private String statusLabel(String status) {
        try {
            ApplicationStatus parsed = ApplicationStatus.parse(status);
            return parsed == null ? "未知阶段" : parsed.label();
        } catch (BusinessException ignored) {
            return "未知阶段";
        }
    }

    private Long applicationId(AiTask task) {
        if (!AiTaskService.JOB_MATCH.equals(task.getTaskType())) return null;
        try {
            JsonNode node = objectMapper.readTree(task.getInputPayload() == null ? "{}" : task.getInputPayload());
            return node.path("applicationId").isNumber() ? node.path("applicationId").asLong() : null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static long value(Long value) { return value == null ? 0 : value; }

    private static final class Filter {
        private final long pageNo;
        private final long pageSize;
        private final Long companyId;
        private final Long positionId;
        private final String status;
        private final String companyKeyword;
        private final String positionKeyword;
        private final String keyword;
        private final LocalDateTime fromTime;
        private final LocalDateTime toTime;
        private final boolean staleOnly;
        private final int staleDays;
        private final LocalDateTime staleBefore;

        private Filter(long pageNo, long pageSize, Long companyId, Long positionId, String status,
                       String companyKeyword, String positionKeyword, String keyword,
                       LocalDateTime fromTime, LocalDateTime toTime, boolean staleOnly, int staleDays) {
            this.pageNo = pageNo;
            this.pageSize = pageSize;
            this.companyId = companyId;
            this.positionId = positionId;
            this.status = status;
            this.companyKeyword = companyKeyword;
            this.positionKeyword = positionKeyword;
            this.keyword = keyword;
            this.fromTime = fromTime;
            this.toTime = toTime;
            this.staleOnly = staleOnly;
            this.staleDays = staleDays;
            this.staleBefore = LocalDateTime.now().minusDays(staleDays);
        }

        private static Filter from(AdminRecruitmentDtos.Query query) {
            long pageNo = query == null || query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
            long pageSize = query == null || query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
            String status = normalize(query == null ? null : query.status());
            if (status != null) ApplicationStatus.parse(status);
            int staleDays = query == null || query.staleDays() == null ? DEFAULT_STALE_DAYS : query.staleDays();
            if (staleDays < 1 || staleDays > MAX_STALE_DAYS) throw BusinessException.badRequest("长时间未推进天数需在 1–365 天之间");
            return new Filter(pageNo, pageSize, query == null ? null : query.companyId(), query == null ? null : query.positionId(),
                    status, normalize(query == null ? null : query.companyKeyword()), normalize(query == null ? null : query.positionKeyword()),
                    normalize(query == null ? null : query.keyword()), parseFrom(query == null ? null : query.from()),
                    parseTo(query == null ? null : query.to()), query != null && Boolean.TRUE.equals(query.staleOnly()), staleDays);
        }

        private static LocalDateTime parseFrom(String value) { return parse(value, false); }
        private static LocalDateTime parseTo(String value) { return parse(value, true); }

        private static LocalDateTime parse(String value, boolean end) {
            if (!StringUtils.hasText(value)) return null;
            try {
                if (value.length() == 10) {
                    LocalDate date = LocalDate.parse(value);
                    return (end ? date.plusDays(1) : date).atStartOfDay();
                }
                return LocalDateTime.parse(value);
            } catch (RuntimeException exception) {
                throw BusinessException.badRequest("招聘运营时间筛选格式无效");
            }
        }

        private static String normalize(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }

        private long offset() { return (pageNo - 1) * pageSize; }
        private long pageNo() { return pageNo; }
        private long pageSize() { return pageSize; }
        private Long companyId() { return companyId; }
        private Long positionId() { return positionId; }
        private String status() { return status; }
        private String companyKeyword() { return companyKeyword; }
        private String positionKeyword() { return positionKeyword; }
        private String keyword() { return keyword; }
        private LocalDateTime fromTime() { return fromTime; }
        private LocalDateTime toTime() { return toTime; }
        private boolean staleOnly() { return staleOnly; }
        private int staleDays() { return staleDays; }
        private LocalDateTime staleBefore() { return staleBefore; }
    }
}
