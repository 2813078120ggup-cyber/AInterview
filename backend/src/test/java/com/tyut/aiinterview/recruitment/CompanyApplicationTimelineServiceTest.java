package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyApplicationTimelineServiceTest {
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final JobApplicationStatusHistoryMapper historyMapper = mock(JobApplicationStatusHistoryMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final OfflineInterviewMapper offlineInterviewMapper = mock(OfflineInterviewMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final CompanyApplicationTimelineService service = new CompanyApplicationTimelineService(companyAccess,
            historyMapper, interviewMapper, offlineInterviewMapper, reportMapper, userMapper);

    @Test
    void companyTimelineCombinesApplicationMatchInterviewReportAndHrEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 10, 0);
        JobApplication application = new JobApplication();
        application.setId(11L);
        application.setCompanyId(100L);
        application.setCandidateId(7L);
        application.setPositionId(31L);
        application.setInterviewId(101L);
        application.setSubmittedAt(now.minusHours(5));
        application.setMatchStartedAt(now.minusHours(4));
        application.setMatchCompletedAt(now.minusHours(3));
        application.setMatchStatus("SUCCESS");
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireApplication(11L)).thenReturn(application);

        JobApplicationStatusHistory history = new JobApplicationStatusHistory();
        history.setId(22L);
        history.setApplicationId(11L);
        history.setToStatus("UNDER_REVIEW");
        history.setOperatorId(88L);
        history.setNote("请进一步确认项目经验");
        history.setCreatedAt(now.minusHours(2));
        when(historyMapper.selectList(any())).thenReturn(List.of(history));

        Interview interview = new Interview();
        interview.setId(101L);
        interview.setTitle("Java 技术面试");
        interview.setCreatedBy(88L);
        interview.setCreatedAt(now.minusHours(2).plusMinutes(5));
        interview.setStartedAt(now.minusHours(1));
        interview.setEndedAt(now.minusMinutes(30));
        when(companyAccess.requireInterviewForApplication(application)).thenReturn(interview);

        Report report = new Report();
        report.setId(91L);
        report.setInterviewId(101L);
        report.setGeneratedBy(88L);
        report.setGeneratedAt(now.minusMinutes(20));
        report.setPublishedAt(now.minusMinutes(10));
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(offlineInterviewMapper.selectOne(any())).thenReturn(null);
        UserAccount operator = new UserAccount();
        operator.setId(88L);
        operator.setRealName("HR 小王");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(operator));

        var events = service.timeline(11L);

        assertEquals(List.of("APPLICATION_SUBMITTED", "MATCH_STARTED", "MATCH_COMPLETED", "HR_ACTION",
                "AI_INTERVIEW_CREATED", "AI_INTERVIEW_STARTED", "AI_INTERVIEW_ENDED", "REPORT_GENERATED", "REPORT_PUBLISHED"),
                events.stream().map(RecruitmentDtos.ApplicationTimelineEventView::type).toList());
        assertEquals("HR 小王", events.get(3).actorName());
        verify(interviewMapper, never()).selectById(any());
    }

    @Test
    void crossCompanyApplicationStopsBeforeTimelineQueries() {
        when(companyAccess.requirePermission("application:read")).thenReturn(200L);
        when(companyAccess.requireApplication(11L)).thenThrow(BusinessException.notFound("申请不存在"));

        assertThrows(BusinessException.class, () -> service.timeline(11L));

        verifyNoInteractions(historyMapper, interviewMapper, offlineInterviewMapper, reportMapper, userMapper);
    }
}
