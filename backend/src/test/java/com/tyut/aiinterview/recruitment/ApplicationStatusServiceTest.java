package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobApplicationStatusHistory;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ApplicationStatusServiceTest {
    private final JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
    private final JobApplicationStatusHistoryMapper historyMapper = mock(JobApplicationStatusHistoryMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final ApplicationStatusService service = new ApplicationStatusService(applicationMapper, historyMapper, currentUser);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        if (TableInfoHelper.getTableInfo(JobApplication.class) == null) {
            TableInfoHelper.initTableInfo(assistant, JobApplication.class);
        }
    }

    @Test
    void exposesTheBackendStateGraphAndTerminalStates() {
        Set<String> submitted = service.allowedTransitions("SUBMITTED").stream()
                .map(RecruitmentDtos.StatusTransition::status).collect(Collectors.toSet());
        Set<String> underReview = service.allowedTransitions("UNDER_REVIEW").stream()
                .map(RecruitmentDtos.StatusTransition::status).collect(Collectors.toSet());

        assertEquals(Set.of("AI_INTERVIEW_PENDING", "UNDER_REVIEW", "REJECTED"), submitted);
        assertEquals(Set.of("AI_INTERVIEW_PENDING", "OFFLINE_INTERVIEW", "REJECTED", "HIRED"), underReview);
        assertTrue(service.allowedTransitions("REJECTED").isEmpty());
        assertTrue(service.allowedTransitions("HIRED").isEmpty());
    }

    @Test
    void validTransitionUpdatesVersionAndWritesHistory() {
        JobApplication application = application("SUBMITTED", 3);
        when(applicationMapper.update(any(), any())).thenReturn(1);

        ApplicationStatusService.TransitionResult result = service.transition(application,
                ApplicationStatus.UNDER_REVIEW, 88L, "完成简历与岗位初筛", null);

        assertEquals(ApplicationStatus.SUBMITTED, result.from());
        assertEquals(ApplicationStatus.UNDER_REVIEW, result.to());
        assertEquals(4, result.version());
        assertEquals("UNDER_REVIEW", application.getStatus());
        assertEquals(4, application.getVersion());
        ArgumentCaptor<JobApplicationStatusHistory> captor = ArgumentCaptor.forClass(JobApplicationStatusHistory.class);
        verify(historyMapper).insert(captor.capture());
        assertEquals("SUBMITTED", captor.getValue().getFromStatus());
        assertEquals("UNDER_REVIEW", captor.getValue().getToStatus());
        assertEquals(88L, captor.getValue().getOperatorId());
        assertEquals("完成简历与岗位初筛", captor.getValue().getNote());
    }

    @Test
    void rejectsIllegalAndTerminalTransitions() {
        JobApplication submitted = application("SUBMITTED", 0);
        BusinessException illegal = assertThrows(BusinessException.class,
                () -> service.transition(submitted, ApplicationStatus.HIRED, 88L, "直接录用", null));
        assertEquals(HttpStatus.BAD_REQUEST, illegal.getStatus());

        JobApplication rejected = application("REJECTED", 2);
        BusinessException terminal = assertThrows(BusinessException.class,
                () -> service.transition(rejected, ApplicationStatus.UNDER_REVIEW, 88L, "恢复", null));
        assertEquals(HttpStatus.BAD_REQUEST, terminal.getStatus());
        verify(applicationMapper, never()).update(any(), any());
    }

    @Test
    void requiresReasonForReviewRejectionAndHiring() {
        for (ApplicationStatus target : List.of(ApplicationStatus.UNDER_REVIEW,
                ApplicationStatus.REJECTED, ApplicationStatus.HIRED)) {
            JobApplication application = application("SUBMITTED", 0);
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.transition(application, target, 88L, " ", null));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        }
        verify(applicationMapper, never()).update(any(), any());
    }

    @Test
    void statusUpdateDtoDeclaresConditionalReasonValidation() {
        assertFalse(new RecruitmentDtos.StatusUpdateRequest("REJECTED", "", null).isReasonValid());
        assertTrue(new RecruitmentDtos.StatusUpdateRequest("REJECTED", "候选人经验不匹配", null).isReasonValid());
        assertTrue(new RecruitmentDtos.StatusUpdateRequest("AI_INTERVIEW_PENDING", null, null).isReasonValid());
    }

    @Test
    void reportsOptimisticConcurrencyConflictAndDoesNotWriteHistory() {
        JobApplication application = application("SUBMITTED", 5);
        when(applicationMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.transition(application, ApplicationStatus.AI_INTERVIEW_PENDING, 88L,
                        "安排 AI 面试", 99L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("SUBMITTED", application.getStatus());
        assertEquals(5, application.getVersion());
        verify(historyMapper, never()).insert(any(JobApplicationStatusHistory.class));
    }

    @Test
    void recordsInitialSubmittedHistory() {
        JobApplication application = application("SUBMITTED", 0);

        service.recordInitial(application, 7L, "候选人通过岗位大厅投递");

        ArgumentCaptor<JobApplicationStatusHistory> captor = ArgumentCaptor.forClass(JobApplicationStatusHistory.class);
        verify(historyMapper).insert(captor.capture());
        assertEquals(null, captor.getValue().getFromStatus());
        assertEquals("SUBMITTED", captor.getValue().getToStatus());
        assertEquals(7L, captor.getValue().getOperatorId());
    }

    private JobApplication application(String status, int version) {
        JobApplication application = new JobApplication();
        application.setId(11L);
        application.setCompanyId(100L);
        application.setCandidateId(7L);
        application.setPositionId(31L);
        application.setStatus(status);
        application.setVersion(version);
        return application;
    }
}
