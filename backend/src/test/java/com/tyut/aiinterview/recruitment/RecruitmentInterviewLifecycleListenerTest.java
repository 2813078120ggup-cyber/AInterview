package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobApplicationStatusHistory;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.interview.InterviewLifecycleEvent;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;

class RecruitmentInterviewLifecycleListenerTest {
    private final JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
    private final JobApplicationStatusHistoryMapper historyMapper = mock(JobApplicationStatusHistoryMapper.class);
    private final JobPositionMapper positionMapper = mock(JobPositionMapper.class);
    private final SiteNotificationService notificationService = mock(SiteNotificationService.class);
    private final ApplicationStatusService statusService = new ApplicationStatusService(applicationMapper, historyMapper,
            mock(CurrentUser.class));
    private final RecruitmentInterviewLifecycleListener listener = new RecruitmentInterviewLifecycleListener(
            applicationMapper, statusService, positionMapper, notificationService);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : java.util.List.of(JobApplication.class, Interview.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void movesPendingApplicationToInterviewingAndRecordsHistory() {
        JobApplication application = application(1L, "AI_INTERVIEW_PENDING");
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(applicationMapper.update(any(), any())).thenReturn(1);

        listener.onInterviewLifecycle(new InterviewLifecycleEvent(2L,
                InterviewLifecycleEvent.Phase.STARTED, 9L));

        ArgumentCaptor<JobApplicationStatusHistory> captor = ArgumentCaptor.forClass(JobApplicationStatusHistory.class);
        verify(historyMapper).insert(captor.capture());
        JobApplicationStatusHistory history = captor.getValue();
        assertEquals("AI_INTERVIEW_PENDING", history.getFromStatus());
        assertEquals("AI_INTERVIEWING", history.getToStatus());
        assertEquals(9L, history.getOperatorId());
    }

    @Test
    void movesInterviewingApplicationToReviewAndNotifiesCandidate() {
        JobApplication application = application(3L, "AI_INTERVIEWING");
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(applicationMapper.update(any(), any())).thenReturn(1);
        JobPosition position = new JobPosition();
        position.setId(4L);
        position.setName("Java 开发工程师");
        when(positionMapper.selectById(4L)).thenReturn(position);

        listener.onInterviewLifecycle(new InterviewLifecycleEvent(5L,
                InterviewLifecycleEvent.Phase.ENDED, 9L));

        verify(historyMapper).insert(any(JobApplicationStatusHistory.class));
        verify(notificationService).create(7L, "APPLICATION_STATUS_CHANGED", "AI 面试已完成",
                "你投递的“Java 开发工程师”已完成 AI 面试，企业将继续查看评测报告。",
                "JOB_APPLICATION", 3L, "ai-interview-completed-2");
    }

    @Test
    void doesNotMoveTerminalApplicationFromInterviewLifecycle() {
        JobApplication application = application(4L, "REJECTED");
        when(applicationMapper.selectOne(any())).thenReturn(application);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> listener.onInterviewLifecycle(new InterviewLifecycleEvent(6L,
                        InterviewLifecycleEvent.Phase.STARTED, 9L)));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    private JobApplication application(Long id, String status) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setInterviewId(2L);
        application.setPositionId(4L);
        application.setCandidateId(7L);
        application.setStatus(status);
        application.setVersion(0);
        return application;
    }
}
