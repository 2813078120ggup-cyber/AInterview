package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.InterviewStatusHistory;
import com.tyut.aiinterview.interview.InterviewLifecycleEvent;
import com.tyut.aiinterview.mapper.InterviewStatusHistoryMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.notification.SiteNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecruitmentInterviewLifecycleListener {
    private final JobApplicationMapper applicationMapper;
    private final ApplicationStatusService statusService;
    private final JobPositionMapper positionMapper;
    private final SiteNotificationService notificationService;
    private InterviewStatusHistoryMapper interviewHistoryMapper;

    public RecruitmentInterviewLifecycleListener(JobApplicationMapper applicationMapper,
                                                 ApplicationStatusService statusService,
                                                 JobPositionMapper positionMapper,
                                                 SiteNotificationService notificationService) {
        this.applicationMapper = applicationMapper;
        this.statusService = statusService;
        this.positionMapper = positionMapper;
        this.notificationService = notificationService;
    }

    @Autowired
    public void setInterviewHistoryMapper(InterviewStatusHistoryMapper interviewHistoryMapper) {
        this.interviewHistoryMapper = interviewHistoryMapper;
    }

    @EventListener
    @Transactional
    public void onInterviewLifecycle(InterviewLifecycleEvent event) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getInterviewId, event.interviewId()).last("LIMIT 1"));
        if (application == null) return;
        if (event.phase() == InterviewLifecycleEvent.Phase.STARTED) {
            recordInterviewHistory(application, event, "PENDING", "IN_PROGRESS", "候选人已开始 AI 面试", "NOT_REQUIRED");
            move(application, ApplicationStatus.AI_INTERVIEW_PENDING, ApplicationStatus.AI_INTERVIEWING,
                    event.operatorId(), "候选人已开始 AI 面试");
            return;
        }
        recordInterviewHistory(application, event, "IN_PROGRESS", "REPORT_GENERATING", "候选人已结束 AI 面试", "SENT");
        if (move(application, ApplicationStatus.AI_INTERVIEWING, ApplicationStatus.UNDER_REVIEW,
                event.operatorId(), "AI 面试已完成，等待企业查看评测报告")) {
            JobPosition position = positionMapper.selectById(application.getPositionId());
            notificationService.create(application.getCandidateId(), "APPLICATION_STATUS_CHANGED", "AI 面试已完成",
                    "你投递的“" + (position == null ? "岗位" : position.getName()) + "”已完成 AI 面试，企业将继续查看评测报告。",
                    "JOB_APPLICATION", application.getId(), "ai-interview-completed-" + application.getInterviewId());
        }
    }

    private void recordInterviewHistory(JobApplication application, InterviewLifecycleEvent event,
                                        String from, String to, String reason, String notificationStatus) {
        if (interviewHistoryMapper == null) return;
        InterviewStatusHistory history = new InterviewStatusHistory();
        history.setInterviewKind("AI");
        history.setInterviewId(event.interviewId());
        history.setApplicationId(application.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorId(event.operatorId());
        history.setReason(reason);
        history.setNotificationStatus(notificationStatus);
        history.setCreatedAt(java.time.LocalDateTime.now());
        interviewHistoryMapper.insert(history);
    }

    private boolean move(JobApplication application, ApplicationStatus from, ApplicationStatus to,
                         Long operatorId, String note) {
        if (to.name().equals(application.getStatus())) return false;
        if (!from.name().equals(application.getStatus())) {
            throw com.tyut.aiinterview.common.BusinessException.badRequest(
                    "申请不能从“" + application.getStatus() + "”按面试生命周期流转到“" + to.name() + "”");
        }
        statusService.transition(application, to, operatorId, note, null);
        return true;
    }
}
