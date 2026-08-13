package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobApplicationStatusHistory;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single write boundary for job application status changes.
 *
 * <p>Every transition is validated against the explicit state graph, updated
 * with the current status and optimistic version, and recorded in the audit
 * history in the same transaction as the application update.</p>
 */
@Service
public class ApplicationStatusService {
    private static final Map<ApplicationStatus, List<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ApplicationStatus.SUBMITTED, List.of(ApplicationStatus.AI_INTERVIEW_PENDING,
                    ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED),
            ApplicationStatus.AI_INTERVIEW_PENDING, List.of(ApplicationStatus.AI_INTERVIEWING,
                    ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED),
            ApplicationStatus.AI_INTERVIEWING, List.of(ApplicationStatus.UNDER_REVIEW,
                    ApplicationStatus.REJECTED),
            ApplicationStatus.UNDER_REVIEW, List.of(ApplicationStatus.AI_INTERVIEW_PENDING,
                    ApplicationStatus.OFFLINE_INTERVIEW, ApplicationStatus.REJECTED, ApplicationStatus.HIRED),
            ApplicationStatus.OFFLINE_INTERVIEW, List.of(ApplicationStatus.HIRED, ApplicationStatus.REJECTED),
            ApplicationStatus.REJECTED, List.of(),
            ApplicationStatus.HIRED, List.of());

    private final JobApplicationMapper applicationMapper;
    private final JobApplicationStatusHistoryMapper historyMapper;
    private final CurrentUser currentUser;

    public ApplicationStatusService(JobApplicationMapper applicationMapper,
                                    JobApplicationStatusHistoryMapper historyMapper,
                                    CurrentUser currentUser) {
        this.applicationMapper = applicationMapper;
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
    }

    public List<RecruitmentDtos.StatusTransition> allowedTransitions(String currentStatus) {
        ApplicationStatus source = ApplicationStatus.parse(currentStatus);
        if (source == null) return List.of();
        return ALLOWED_TRANSITIONS.getOrDefault(source, List.of()).stream()
                .map(target -> new RecruitmentDtos.StatusTransition(target.name(), target.label(), targetRequiresReason(target)))
                .toList();
    }

    public void initializeSubmitted(JobApplication application) {
        if (application == null || (application.getStatus() != null
                && ApplicationStatus.parse(application.getStatus()) != ApplicationStatus.SUBMITTED)) {
            throw BusinessException.badRequest("申请初始状态不合法");
        }
        application.setStatus(ApplicationStatus.SUBMITTED.name());
    }

    @Transactional
    public TransitionResult transition(JobApplication application, ApplicationStatus target,
                                       Long operatorId, String reason, Long interviewId) {
        if (application == null || application.getId() == null) {
            throw BusinessException.notFound("申请不存在");
        }
        ApplicationStatus source = ApplicationStatus.parse(application.getStatus());
        if (source == null || target == null || !ALLOWED_TRANSITIONS.getOrDefault(source, List.of()).contains(target)) {
            String sourceLabel = source == null ? "未知状态" : source.name();
            String targetLabel = target == null ? "未知状态" : target.name();
            throw BusinessException.badRequest("申请不能从“" + sourceLabel + "”流转到“" + targetLabel + "”");
        }
        String note = normalizeReason(reason);
        if (targetRequiresReason(target) && note == null) {
            throw BusinessException.badRequest("转为“" + target.label() + "”时必须填写审核备注或变更原因");
        }

        int version = application.getVersion() == null ? 0 : application.getVersion();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<JobApplication> update = new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, application.getId())
                .eq(JobApplication::getStatus, source.name())
                .eq(JobApplication::getVersion, version)
                .set(JobApplication::getStatus, target.name())
                .set(JobApplication::getVersion, version + 1)
                .set(JobApplication::getReviewedAt, now)
                .set(JobApplication::getReviewNote, note);
        if (application.getCompanyId() != null) {
            update.eq(JobApplication::getCompanyId, application.getCompanyId());
        }
        if (interviewId != null) update.set(JobApplication::getInterviewId, interviewId);
        if (applicationMapper.update(null, update) == 0) {
            throw BusinessException.conflict("申请状态或版本已变化，请刷新后重试");
        }

        JobApplicationStatusHistory history = new JobApplicationStatusHistory();
        history.setApplicationId(application.getId());
        history.setFromStatus(source.name());
        history.setToStatus(target.name());
        history.setOperatorId(resolveOperatorId(application, operatorId));
        history.setNote(note);
        historyMapper.insert(history);

        application.setStatus(target.name());
        application.setVersion(version + 1);
        application.setReviewedAt(now);
        application.setReviewNote(note);
        if (interviewId != null) application.setInterviewId(interviewId);
        return new TransitionResult(source, target, version + 1);
    }

    @Transactional
    public void recordInitial(JobApplication application, Long operatorId, String reason) {
        if (application == null || application.getId() == null
                || ApplicationStatus.parse(application.getStatus()) != ApplicationStatus.SUBMITTED) {
            throw BusinessException.badRequest("申请初始状态不合法");
        }
        JobApplicationStatusHistory history = new JobApplicationStatusHistory();
        history.setApplicationId(application.getId());
        history.setFromStatus(null);
        history.setToStatus(ApplicationStatus.SUBMITTED.name());
        history.setOperatorId(resolveOperatorId(application, operatorId));
        history.setNote(normalizeReason(reason));
        historyMapper.insert(history);
    }

    private Long resolveOperatorId(JobApplication application, Long operatorId) {
        if (operatorId != null) return operatorId;
        if (application.getCandidateId() != null) return application.getCandidateId();
        return currentUser.id();
    }

    private static boolean targetRequiresReason(ApplicationStatus target) {
        return target == ApplicationStatus.UNDER_REVIEW
                || target == ApplicationStatus.REJECTED
                || target == ApplicationStatus.HIRED;
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.trim().isEmpty() ? null : reason.trim();
    }

    public record TransitionResult(ApplicationStatus from, ApplicationStatus to, int version) {
    }
}
