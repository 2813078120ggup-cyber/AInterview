package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobApplicationStatusHistory;
import com.tyut.aiinterview.domain.OfflineInterview;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Builds a small, company-scoped activity stream for an application. Large
 * resume files, recordings and question/answer payloads are intentionally not
 * part of this response.
 */
@Service
public class CompanyApplicationTimelineService {
    private final CompanyAccessService companyAccess;
    private final JobApplicationStatusHistoryMapper historyMapper;
    private final InterviewMapper interviewMapper;
    private final OfflineInterviewMapper offlineInterviewMapper;
    private final ReportMapper reportMapper;
    private final UserMapper userMapper;

    public CompanyApplicationTimelineService(CompanyAccessService companyAccess,
                                             JobApplicationStatusHistoryMapper historyMapper,
                                             InterviewMapper interviewMapper,
                                             OfflineInterviewMapper offlineInterviewMapper,
                                             ReportMapper reportMapper, UserMapper userMapper) {
        this.companyAccess = companyAccess;
        this.historyMapper = historyMapper;
        this.interviewMapper = interviewMapper;
        this.offlineInterviewMapper = offlineInterviewMapper;
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
    }

    public List<RecruitmentDtos.ApplicationTimelineEventView> timeline(Long applicationId) {
        companyAccess.requirePermission("application:read");
        JobApplication application = companyAccess.requireApplication(applicationId);
        List<RawEvent> events = new ArrayList<>();
        Set<Long> actorIds = new HashSet<>();
        add(events, "application-submitted-" + applicationId, "APPLICATION_SUBMITTED", "已投递申请",
                "候选人提交了该岗位申请。", null, application.getSubmittedAt(), "default");

        if (application.getMatchStartedAt() != null) {
            add(events, "match-started-" + applicationId, "MATCH_STARTED", "匹配任务已开始",
                    "系统开始根据岗位与简历生成匹配评估。", null, application.getMatchStartedAt(), "info");
        }
        if (application.getMatchCompletedAt() != null) {
            boolean failed = "FAILED".equalsIgnoreCase(application.getMatchStatus());
            add(events, "match-completed-" + applicationId, "MATCH_COMPLETED", failed ? "匹配任务失败" : "匹配任务已完成",
                    failed ? safe(application.getMatchError(), "匹配任务未完成，请检查匹配结果。") : "匹配评估结果已写入申请。",
                    null, application.getMatchCompletedAt(), failed ? "danger" : "success");
        }

        List<JobApplicationStatusHistory> history = historyMapper.selectList(new LambdaQueryWrapper<JobApplicationStatusHistory>()
                .eq(JobApplicationStatusHistory::getApplicationId, applicationId)
                .orderByAsc(JobApplicationStatusHistory::getCreatedAt)
                .orderByAsc(JobApplicationStatusHistory::getId));
        for (JobApplicationStatusHistory item : history) {
            if (item.getOperatorId() != null) actorIds.add(item.getOperatorId());
            String stage = stageLabel(item.getToStatus());
            add(events, "status-" + item.getId(), "HR_ACTION", "阶段变化：" + stage,
                    item.getNote(), item.getOperatorId(), item.getCreatedAt(), toneForStatus(item.getToStatus()));
        }

        Interview interview = null;
        if (application.getInterviewId() != null) {
            // This validates candidate, position and application interview link
            // before any interview/report data is exposed.
            interview = companyAccess.requireInterviewForApplication(application);
            if (interview.getCreatedBy() != null) actorIds.add(interview.getCreatedBy());
            add(events, "ai-created-" + interview.getId(), "AI_INTERVIEW_CREATED", "AI 面试已创建",
                    interview.getTitle(), interview.getCreatedBy(), interview.getCreatedAt(), "info");
            if (interview.getStartedAt() != null) {
                add(events, "ai-started-" + interview.getId(), "AI_INTERVIEW_STARTED", "AI 面试已开始",
                        "候选人进入面试流程。", null, interview.getStartedAt(), "info");
            }
            if (interview.getEndedAt() != null) {
                add(events, "ai-ended-" + interview.getId(), "AI_INTERVIEW_ENDED", "AI 面试已结束",
                        "面试作答已结束，等待评估报告。", null, interview.getEndedAt(), "success");
            }
            Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                    .eq(Report::getInterviewId, interview.getId()));
            if (report != null) {
                if (report.getGeneratedBy() != null) actorIds.add(report.getGeneratedBy());
                add(events, "report-generated-" + report.getId(), "REPORT_GENERATED", "评估报告已生成",
                        "报告内容已生成，等待发布或人工确认。", report.getGeneratedBy(), report.getGeneratedAt(), "success");
                if (report.getPublishedAt() != null) {
                    add(events, "report-published-" + report.getId(), "REPORT_PUBLISHED", "评估报告已发布",
                            "报告已对候选人开放查看。", report.getGeneratedBy(), report.getPublishedAt(), "success");
                }
            }
        }

        OfflineInterview offline = offlineInterviewMapper.selectOne(new LambdaQueryWrapper<OfflineInterview>()
                .eq(OfflineInterview::getApplicationId, applicationId)
                .eq(OfflineInterview::getCompanyId, application.getCompanyId())
                .orderByDesc(OfflineInterview::getCreatedAt)
                .last("LIMIT 1"));
        if (offline != null) {
            if (offline.getCreatedBy() != null) actorIds.add(offline.getCreatedBy());
            add(events, "offline-invited-" + offline.getId(), "OFFLINE_INTERVIEW_INVITED", "线下面试邀请已发送",
                    offlineDescription(offline), offline.getCreatedBy(), offline.getCreatedAt(), "warning");
        }

        Map<Long, String> actorNames = actorIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(actorIds).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, this::actorName, (left, right) -> left));
        return events.stream()
                .sorted(Comparator.comparing(RawEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RawEvent::id))
                .map(event -> new RecruitmentDtos.ApplicationTimelineEventView(event.id(), event.type(), event.title(),
                        event.description(), event.actorId() == null ? "系统" : actorNames.getOrDefault(event.actorId(), "系统"),
                        event.occurredAt(), event.tone()))
                .toList();
    }

    private void add(List<RawEvent> events, String id, String type, String title, String description,
                     Long actorId, LocalDateTime occurredAt, String tone) {
        if (occurredAt != null) events.add(new RawEvent(id, type, title, description, actorId, occurredAt, tone));
    }

    private String offlineDescription(OfflineInterview interview) {
        String mode = switch (safe(interview.getInterviewType(), "").toUpperCase()) {
            case "VIDEO" -> "视频面试";
            case "PHONE" -> "电话面试";
            default -> "现场面试";
        };
        return mode + " · " + (interview.getScheduledAt() == null ? "时间待定" : interview.getScheduledAt());
    }

    private String actorName(UserAccount user) {
        if (user == null) return "系统";
        return user.getRealName() == null || user.getRealName().isBlank() ? user.getUsername() : user.getRealName();
    }

    private String stageLabel(String status) {
        if (status == null) return "申请状态更新";
        return switch (status) {
            case "SUBMITTED" -> "已投递";
            case "AI_INTERVIEW_PENDING" -> "待 AI 面试";
            case "AI_INTERVIEWING" -> "AI 面试中";
            case "UNDER_REVIEW" -> "企业评估中";
            case "OFFLINE_INTERVIEW" -> "线下面试";
            case "REJECTED" -> "未通过";
            case "HIRED" -> "已录用";
            default -> status;
        };
    }

    private String toneForStatus(String status) {
        return switch (status == null ? "" : status) {
            case "HIRED" -> "success";
            case "REJECTED" -> "danger";
            case "AI_INTERVIEW_PENDING", "OFFLINE_INTERVIEW" -> "warning";
            default -> "info";
        };
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record RawEvent(String id, String type, String title, String description, Long actorId,
                            LocalDateTime occurredAt, String tone) {}
}
