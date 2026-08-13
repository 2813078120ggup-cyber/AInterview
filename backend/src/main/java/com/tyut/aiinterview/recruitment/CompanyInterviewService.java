package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.InterviewStatusHistory;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.OfflineInterview;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.CompanyInterviewMapper;
import com.tyut.aiinterview.mapper.CompanyInterviewRow;
import com.tyut.aiinterview.mapper.InterviewStatusHistoryMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyInterviewService {
    private static final Set<String> RANGES = Set.of("TODAY", "NEXT_7_DAYS", "COMPLETED", "CANCELLED", "ALL");
    private static final Set<String> ACTIVITY_TYPES = Set.of("AI", "ONSITE", "VIDEO", "PHONE");
    private static final Set<String> SORTS = Set.of("SOONEST", "NEWEST", "CANDIDATE");

    private final CompanyAccessService companyAccess;
    private final CompanyInterviewMapper interviewMapper;
    private final OfflineInterviewMapper offlineMapper;
    private final InterviewStatusHistoryMapper historyMapper;
    private final UserMapper userMapper;
    private final AiTaskMapper aiTaskMapper;
    private final AiTaskService aiTaskService;
    private final SiteNotificationService notificationService;
    private final CurrentUser currentUser;
    private final OperationAuditService auditService;

    public CompanyInterviewService(CompanyAccessService companyAccess, CompanyInterviewMapper interviewMapper,
                                   OfflineInterviewMapper offlineMapper, InterviewStatusHistoryMapper historyMapper,
                                   UserMapper userMapper, AiTaskMapper aiTaskMapper, AiTaskService aiTaskService,
                                   SiteNotificationService notificationService, CurrentUser currentUser) {
        this(companyAccess, interviewMapper, offlineMapper, historyMapper, userMapper, aiTaskMapper, aiTaskService,
                notificationService, currentUser, null);
    }

    @Autowired
    public CompanyInterviewService(CompanyAccessService companyAccess, CompanyInterviewMapper interviewMapper,
                                   OfflineInterviewMapper offlineMapper, InterviewStatusHistoryMapper historyMapper,
                                   UserMapper userMapper, AiTaskMapper aiTaskMapper, AiTaskService aiTaskService,
                                   SiteNotificationService notificationService, CurrentUser currentUser,
                                   OperationAuditService auditService) {
        this.companyAccess = companyAccess;
        this.interviewMapper = interviewMapper;
        this.offlineMapper = offlineMapper;
        this.historyMapper = historyMapper;
        this.userMapper = userMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.aiTaskService = aiTaskService;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public CompanyInterviewDtos.Page page(CompanyInterviewDtos.Query query) {
        Long companyId = companyAccess.requirePermission("application:read");
        int pageNo = pageNo(query == null ? null : query.pageNo());
        int pageSize = pageSize(query == null ? null : query.pageSize());
        String range = normalize(query == null ? null : query.range(), "TODAY");
        String activityType = normalizeNullable(query == null ? null : query.activityType());
        String sort = normalize(query == null ? null : query.sort(), "SOONEST");
        if (!RANGES.contains(range)) throw BusinessException.badRequest("面试时间筛选不合法");
        if (activityType != null && !ACTIVITY_TYPES.contains(activityType)) {
            throw BusinessException.badRequest("面试形式筛选不合法");
        }
        if (!SORTS.contains(sort)) throw BusinessException.badRequest("面试排序方式不合法");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = null;
        LocalDateTime to = null;
        if ("TODAY".equals(range)) {
            from = LocalDate.now().atStartOfDay();
            to = from.plusDays(1);
        } else if ("NEXT_7_DAYS".equals(range)) {
            from = LocalDate.now().atStartOfDay();
            to = from.plusDays(7);
        }
        boolean restricted = companyAccess.isRestrictedInterviewer();
        String keyword = normalizeNullable(query == null ? null : query.keyword());
        Long positionId = query == null ? null : query.positionId();
        long total = interviewMapper.count(companyId, currentUser.id(), restricted, null, positionId, keyword,
                activityType, range, from, to);
        List<CompanyInterviewDtos.Item> records = interviewMapper.selectPage(companyId, currentUser.id(), restricted,
                        null, positionId, keyword, activityType, range, from, to, sort,
                        (pageNo - 1) * pageSize, pageSize).stream().map(this::toItem).toList();
        return new CompanyInterviewDtos.Page(records, total, pageNo, pageSize, now);
    }

    public CompanyInterviewDtos.Detail detail(String activityId) {
        companyAccess.requirePermission("application:read");
        CompanyInterviewRow row = findRow(activityId);
        JobApplication application = companyAccess.requireApplication(row.applicationId());
        if ("AI".equals(row.interviewKind())) {
            companyAccess.requireInterviewForApplication(application);
        } else {
            OfflineInterview offline = offlineMapper.selectById(row.offlineInterviewId());
            if (offline == null || !application.getId().equals(offline.getApplicationId())
                    || !application.getCompanyId().equals(offline.getCompanyId())) {
                throw BusinessException.notFound("面试不存在");
            }
        }
        AiTask task = row.interviewId() == null ? null : aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getInterviewId, row.interviewId())
                .eq(AiTask::getTaskType, AiTaskService.AUTO_EVALUATION)
                .orderByDesc(AiTask::getId).last("LIMIT 1"));
        List<CompanyInterviewDtos.StatusHistory> history = historyMapper.selectList(new LambdaQueryWrapper<InterviewStatusHistory>()
                        .eq("AI".equals(row.interviewKind()) ? InterviewStatusHistory::getInterviewId
                                : InterviewStatusHistory::getOfflineInterviewId,
                                "AI".equals(row.interviewKind()) ? row.interviewId() : row.offlineInterviewId())
                        .orderByAsc(InterviewStatusHistory::getCreatedAt).orderByAsc(InterviewStatusHistory::getId))
                .stream().map(this::toHistory).toList();
        return new CompanyInterviewDtos.Detail(toItem(row), task == null ? null : task.getStatus(),
                task == null ? null : task.getAttempts(), task == null || !"FAILED".equals(task.getStatus())
                ? null : "AI 评测任务失败，可重试。", history);
    }

    @Transactional
    public CompanyInterviewDtos.Detail reschedule(String activityId, CompanyInterviewDtos.RescheduleRequest request) {
        companyAccess.requirePermission("interview:review");
        CompanyInterviewRow row = requireOffline(activityId);
        requireScheduled(row);
        if (!request.scheduledAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("面试时间必须晚于后端当前时间");
        }
        OfflineInterview current = offlineMapper.selectById(row.offlineInterviewId());
        int updated = offlineMapper.update(current, new LambdaUpdateWrapper<OfflineInterview>()
                .eq(OfflineInterview::getId, row.offlineInterviewId())
                .eq(OfflineInterview::getStatus, "SCHEDULED")
                .set(OfflineInterview::getScheduledAt, request.scheduledAt())
                .set(OfflineInterview::getDurationMinutes, request.durationMinutes()));
        if (updated == 0) throw BusinessException.conflict("面试状态已变化，请刷新后重试");
        String reason = textOrDefault(request.reason(), "线下面试已改期");
        notifyCandidate(row, "INTERVIEW_RESCHEDULED", "面试安排已更新", "你的线下面试已改期至 " + request.scheduledAt().toString().replace('T', ' ') + "。",
                "rescheduled-" + request.scheduledAt());
        record(row, "SCHEDULED", "SCHEDULED", reason, "SENT");
        audit("INTERVIEW_RESCHEDULED", row, "线下面试已改期");
        return detail(activityId);
    }

    @Transactional
    public CompanyInterviewDtos.Detail cancel(String activityId, CompanyInterviewDtos.ActionRequest request) {
        companyAccess.requirePermission("interview:review");
        CompanyInterviewRow row = requireOffline(activityId);
        requireScheduled(row);
        OfflineInterview current = offlineMapper.selectById(row.offlineInterviewId());
        int updated = offlineMapper.update(current, new LambdaUpdateWrapper<OfflineInterview>()
                .eq(OfflineInterview::getId, row.offlineInterviewId())
                .eq(OfflineInterview::getStatus, "SCHEDULED")
                .set(OfflineInterview::getStatus, "CANCELLED"));
        if (updated == 0) throw BusinessException.conflict("面试状态已变化，请刷新后重试");
        String reason = textOrDefault(request == null ? null : request.reason(), "线下面试已取消");
        notifyCandidate(row, "INTERVIEW_CANCELLED", "线下面试已取消", "你收到的线下面试安排已取消，请关注后续通知。", "cancelled");
        record(row, "SCHEDULED", "CANCELLED", reason, "SENT");
        audit("INTERVIEW_CANCELLED", row, "取消线下面试");
        return detail(activityId);
    }

    @Transactional
    public CompanyInterviewDtos.Detail complete(String activityId, CompanyInterviewDtos.ActionRequest request) {
        companyAccess.requirePermission("interview:review");
        CompanyInterviewRow row = requireOffline(activityId);
        requireScheduled(row);
        if (row.scheduledAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("面试尚未到达安排时间，不能提前标记完成");
        }
        OfflineInterview current = offlineMapper.selectById(row.offlineInterviewId());
        int updated = offlineMapper.update(current, new LambdaUpdateWrapper<OfflineInterview>()
                .eq(OfflineInterview::getId, row.offlineInterviewId())
                .eq(OfflineInterview::getStatus, "SCHEDULED")
                .set(OfflineInterview::getStatus, "COMPLETED"));
        if (updated == 0) throw BusinessException.conflict("面试状态已变化，请刷新后重试");
        String reason = textOrDefault(request == null ? null : request.reason(), "线下面试已完成");
        notifyCandidate(row, "APPLICATION_STATUS_CHANGED", "线下面试已完成", "你与企业的线下面试已完成，企业将继续更新申请进度。", "completed");
        record(row, "SCHEDULED", "COMPLETED", reason, "SENT");
        audit("INTERVIEW_COMPLETED", row, "完成线下面试");
        return detail(activityId);
    }

    @Transactional
    public CompanyInterviewDtos.RetryView retry(String activityId) {
        companyAccess.requirePermission("interview:review");
        CompanyInterviewRow row = findRow(activityId);
        if (!"AI".equals(row.interviewKind()) || row.interviewId() == null) {
            throw BusinessException.badRequest("只有 AI 面试评测任务支持重试");
        }
        CompanyInterviewDtos.Detail current = detail(activityId);
        if (!"FAILED".equals(current.aiTaskStatus()) && row.rawStatus() != 7) {
            throw BusinessException.badRequest("当前 AI 面试没有失败的评测任务");
        }
        AiTask task = aiTaskService.retryAutomaticEvaluation(row.interviewId());
        audit("INTERVIEW_EVALUATION_RETRIED", row, "重试 AI 面试评测任务");
        return new CompanyInterviewDtos.RetryView(task.getStatus(), task.getAttempts(), "AI 评测任务已重新排队");
    }

    private CompanyInterviewRow findRow(String activityId) {
        if (activityId == null || activityId.isBlank()) throw BusinessException.notFound("面试不存在");
        boolean restricted = companyAccess.isRestrictedInterviewer();
        Long companyId = companyAccess.requireCompanyId();
        try {
            if (activityId.startsWith("AI-")) {
                return requireRow(interviewMapper.selectAi(companyId, currentUser.id(), restricted,
                        Long.valueOf(activityId.substring(3))));
            }
            if (activityId.startsWith("OFFLINE-")) {
                if (restricted) throw BusinessException.notFound("面试不存在");
                return requireRow(interviewMapper.selectOffline(companyId, currentUser.id(), false,
                        Long.valueOf(activityId.substring(8))));
            }
        } catch (NumberFormatException exception) {
            throw BusinessException.notFound("面试不存在");
        }
        throw BusinessException.notFound("面试不存在");
    }

    private CompanyInterviewRow requireRow(CompanyInterviewRow row) {
        if (row == null) throw BusinessException.notFound("面试不存在");
        return row;
    }

    private CompanyInterviewRow requireOffline(String activityId) {
        CompanyInterviewRow row = findRow(activityId);
        if (!"OFFLINE".equals(row.interviewKind())) throw BusinessException.badRequest("只有线下面试支持该操作");
        return row;
    }

    private void requireScheduled(CompanyInterviewRow row) {
        if (!"SCHEDULED".equals(row.status())) throw BusinessException.badRequest("当前面试状态不允许该操作");
    }

    private void record(CompanyInterviewRow row, String from, String to, String reason, String notificationStatus) {
        InterviewStatusHistory history = new InterviewStatusHistory();
        history.setInterviewKind(row.interviewKind());
        history.setInterviewId(row.interviewId());
        history.setOfflineInterviewId(row.offlineInterviewId());
        history.setApplicationId(row.applicationId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorId(currentUser.id());
        history.setReason(reason);
        history.setNotificationStatus(notificationStatus);
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private void notifyCandidate(CompanyInterviewRow row, String eventType, String title, String content, String suffix) {
        notificationService.create(row.candidateId(), eventType, title, content,
                "JOB_APPLICATION", row.applicationId(), "company-interview-" + row.activityId() + "-" + suffix);
    }

    private void audit(String action, CompanyInterviewRow row, String summary) {
        if (auditService != null) auditService.success("INTERVIEW", action, "INTERVIEW_ACTIVITY",
                row.activityId(), row.companyId(), summary + "，关联申请 " + row.applicationId());
    }

    private CompanyInterviewDtos.Item toItem(CompanyInterviewRow row) {
        return new CompanyInterviewDtos.Item(row.activityId(), row.interviewKind(), row.interviewId(), row.offlineInterviewId(),
                row.applicationId(), row.positionId(), row.positionName(), row.candidateId(), row.candidateName(),
                row.candidateEmail(), row.candidatePhone(), row.activityType(), row.rawStatus(), row.status(),
                row.scheduledAt(), row.durationMinutes(), row.location(), row.meetingUrl(), row.contactName(),
                row.contactPhone(), row.note(), row.applicationStatus(), row.notificationStatus(), row.updatedAt());
    }

    private CompanyInterviewDtos.StatusHistory toHistory(InterviewStatusHistory item) {
        UserAccount operator = item.getOperatorId() == null ? null : userMapper.selectById(item.getOperatorId());
        return new CompanyInterviewDtos.StatusHistory(item.getInterviewKind(), item.getFromStatus(), item.getToStatus(),
                item.getReason(), item.getNotificationStatus(), operator == null ? "系统" : operator.getRealName(), item.getCreatedAt());
    }

    private int pageNo(Integer value) { return value == null ? 1 : Math.max(1, Math.min(100000, value)); }
    private int pageSize(Integer value) { return value == null ? 20 : Math.min(100, Math.max(1, value)); }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
