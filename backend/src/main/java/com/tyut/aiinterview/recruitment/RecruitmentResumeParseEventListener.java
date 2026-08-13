package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RecruitmentResumeParseEventListener {
    private final JobApplicationMapper applicationMapper;
    private final AiTaskService taskService;

    public RecruitmentResumeParseEventListener(JobApplicationMapper applicationMapper, AiTaskService taskService) {
        this.applicationMapper = applicationMapper;
        this.taskService = taskService;
    }

    @EventListener
    public void onResumeParsed(ResumeParseCompletedEvent event) {
        List<JobApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getResumeId, event.resumeId()));
        if (!event.success()) {
            applications.stream().filter(item -> !"SUCCESS".equals(item.getMatchStatus()))
                    .forEach(item -> applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                            .eq(JobApplication::getId, item.getId())
                            .set(JobApplication::getMatchStatus, "FAILED")
                            .set(JobApplication::getMatchError, event.error())));
            return;
        }
        for (JobApplication application : applications) {
            boolean sameResumeVersion = event.version().equals(application.getMatchVersion());
            if (sameResumeVersion && "SUCCESS".equals(application.getMatchStatus())) continue;
            int currentEvaluationVersion = application.getMatchEvaluationVersion() == null ? 0 : application.getMatchEvaluationVersion();
            int evaluationVersion = sameResumeVersion ? Math.max(1, currentEvaluationVersion) : Math.max(1, currentEvaluationVersion + 1);
            int currentApplicationVersion = application.getVersion() == null ? 0 : application.getVersion();
            int updated = applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                    .eq(JobApplication::getId, application.getId())
                    .eq(JobApplication::getVersion, currentApplicationVersion)
                    .set(JobApplication::getMatchStatus, "PENDING")
                    .set(JobApplication::getMatchVersion, event.version())
                    .set(JobApplication::getMatchEvaluationVersion, evaluationVersion)
                    .set(JobApplication::getMatchError, null)
                    .set(JobApplication::getMatchCompletedAt, null)
                    .set(JobApplication::getVersion, currentApplicationVersion + 1));
            if (updated == 0) continue;
            taskService.enqueueJobMatch(application.getId(), application.getPositionId(), event.resumeId(), event.version(),
                    evaluationVersion, application.getCandidateId());
        }
    }
}
