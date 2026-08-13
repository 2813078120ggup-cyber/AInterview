package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.interview.InterviewService;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.mapper.JobMatchEvaluationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecruitmentApplicationQueryTest {
    private final JobPositionMapper positionMapper = org.mockito.Mockito.mock(JobPositionMapper.class);
    private final CompanyMapper companyMapper = org.mockito.Mockito.mock(CompanyMapper.class);
    private final JobApplicationMapper applicationMapper = org.mockito.Mockito.mock(JobApplicationMapper.class);
    private final CandidateResumeMapper resumeMapper = org.mockito.Mockito.mock(CandidateResumeMapper.class);
    private final JobMatchEvaluationMapper matchEvaluationMapper = org.mockito.Mockito.mock(JobMatchEvaluationMapper.class);
    private final JobApplicationStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(JobApplicationStatusHistoryMapper.class);
    private final OfflineInterviewMapper offlineInterviewMapper = org.mockito.Mockito.mock(OfflineInterviewMapper.class);
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final InterviewMapper interviewMapper = org.mockito.Mockito.mock(InterviewMapper.class);
    private final QuestionBankMapper questionBankMapper = org.mockito.Mockito.mock(QuestionBankMapper.class);
    private final InterviewService interviewService = org.mockito.Mockito.mock(InterviewService.class);
    private final SiteNotificationService notificationService = org.mockito.Mockito.mock(SiteNotificationService.class);
    private final CurrentUser currentUser = org.mockito.Mockito.mock(CurrentUser.class);
    private final AiTaskService taskService = org.mockito.Mockito.mock(AiTaskService.class);
    private final CompanyAccessService companyAccess = org.mockito.Mockito.mock(CompanyAccessService.class);
    private final ApplicationStatusService statusService = org.mockito.Mockito.mock(ApplicationStatusService.class);
    private final RecruitmentAuditService auditService = org.mockito.Mockito.mock(RecruitmentAuditService.class);
    private RecruitmentService service;

    @BeforeEach
    void setUp() {
        service = new RecruitmentService(positionMapper, companyMapper, applicationMapper, resumeMapper,
                matchEvaluationMapper, historyMapper, offlineInterviewMapper, userMapper, interviewMapper,
                questionBankMapper, interviewService, notificationService, currentUser, new ObjectMapper(),
                taskService, companyAccess, statusService, auditService);
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(applicationMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(positionMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void companyQueryUsesServerPaginationCompanyScopeFiltersAndSafeSort() {
        RecruitmentDtos.ApplicationQuery query = new RecruitmentDtos.ApplicationQuery(
                2L, 25L, "java", "UNDER_REVIEW", 7L,
                new BigDecimal("60"), new BigDecimal("95"),
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 11, 23, 59),
                "OFFLINE_SCHEDULED", "OLDEST_UNPROCESSED");

        service.companyApplications(query);

        ArgumentCaptor<Page<JobApplication>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(applicationMapper).selectPage(pageCaptor.capture(), any());
        verify(companyAccess).applyApplicationScope(any());
        assertEquals(2L, pageCaptor.getValue().getCurrent());
        assertEquals(25L, pageCaptor.getValue().getSize());
    }

    @Test
    void invalidInterviewFilterIsRejectedBeforeQuery() {
        RecruitmentDtos.ApplicationQuery query = new RecruitmentDtos.ApplicationQuery(
                1L, 10L, null, null, null, null, null, null, null, "UNKNOWN", "LATEST");

        org.junit.jupiter.api.Assertions.assertThrows(com.tyut.aiinterview.common.BusinessException.class,
                () -> service.companyApplications(query));
    }

    @Test
    void activityCreationLocksApplicationBeforeCheckingExistingInterview() {
        when(companyAccess.requirePermission("interview:create")).thenReturn(100L);
        JobApplication application = new JobApplication();
        application.setId(2L);
        application.setCompanyId(100L);
        application.setStatus("SUBMITTED");
        application.setInterviewId(77L);
        when(applicationMapper.selectForUpdate(2L)).thenReturn(application);

        org.junit.jupiter.api.Assertions.assertThrows(com.tyut.aiinterview.common.BusinessException.class,
                () -> service.createAiInterview(2L, new RecruitmentDtos.AiInterviewRequest(
                        LocalDateTime.now().plusHours(2), 60, "tech", 1L, 5, "big-tech", null)));

        verify(applicationMapper).selectForUpdate(2L);
        org.mockito.Mockito.verify(applicationMapper, org.mockito.Mockito.never()).selectById(2L);
    }
}
