package com.tyut.aiinterview.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.report.ReportDtos.ReportListItem;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportServiceTest {
    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(Interview.class, Report.class, UserAccount.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void warnsWhenInterviewContainsTooFewQuestions() {
        String warning = ReportService.reliabilityWarning(1);

        assertTrue(warning.contains("仅包含 1 道题"));
        assertTrue(warning.contains("参考性有限"));
        assertNull(ReportService.reliabilityWarning(5));
    }

    @Test
    void companyReportUsesOnlyTheHrAllowlist() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        CompanyAccessService companyAccess = mock(CompanyAccessService.class);
        Report report = new Report();
        report.setId(91L);
        report.setInterviewId(101L);
        report.setTotalScore(new java.math.BigDecimal("86.5"));
        report.setSummary("综合评估");
        report.setScoringPromptCode("prompt-secret");
        report.setReportPromptCode("report-prompt-secret");
        when(companyAccess.requireReportForApplication(11L)).thenReturn(report);
        when(questionMapper.selectCount(any())).thenReturn(5L);
        ReportService service = new ReportService(reportMapper, interviewMapper, questionMapper,
                mock(EvaluationMapper.class), mock(UserMapper.class), currentUser, mock(DeepSeekGateway.class), companyAccess);

        ReportDtos.CompanyReportDetail detail = service.companyDetail(11L);

        assertEquals(11L, detail.applicationId());
        assertEquals(new java.math.BigDecimal("86.5"), detail.totalScore());
        assertFalse(detail.toString().contains("prompt-secret"));
        assertFalse(detail.toString().contains("report-prompt-secret"));
    }

    @Test
    void companyAdminCannotUseGenericReportEndpoint() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(88L);
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.hasRole("COMPANY_ADMIN")).thenReturn(true);
        when(currentUser.hasCompanyRole()).thenReturn(true);
        Interview interview = new Interview();
        interview.setId(101L);
        interview.setCandidateId(7L);
        when(interviewMapper.selectById(101L)).thenReturn(interview);
        ReportService service = new ReportService(reportMapper, interviewMapper,
                mock(InterviewQuestionMapper.class), mock(EvaluationMapper.class), mock(UserMapper.class),
                currentUser, mock(DeepSeekGateway.class), mock(CompanyAccessService.class));

        var exception = assertThrows(com.tyut.aiinterview.common.BusinessException.class,
                () -> service.detail(101L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(reportMapper, never()).selectOne(any());
    }

    @Test
    void candidateStillCannotReadUnpublishedReport() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.hasRole("COMPANY_ADMIN")).thenReturn(false);
        Interview interview = new Interview();
        interview.setId(101L);
        interview.setCandidateId(7L);
        when(interviewMapper.selectById(101L)).thenReturn(interview);
        Report report = new Report();
        report.setInterviewId(101L);
        report.setStatus(0);
        when(reportMapper.selectOne(any())).thenReturn(report);
        ReportService service = new ReportService(reportMapper, interviewMapper,
                mock(InterviewQuestionMapper.class), mock(EvaluationMapper.class), mock(UserMapper.class),
                currentUser, mock(DeepSeekGateway.class), mock(CompanyAccessService.class));

        var exception = assertThrows(com.tyut.aiinterview.common.BusinessException.class,
                () -> service.detail(101L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void superAdminCanReadReportThroughGenericAdminEndpoint() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(999L);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(currentUser.hasRole("COMPANY_ADMIN")).thenReturn(false);
        Interview interview = new Interview();
        interview.setId(101L);
        interview.setCandidateId(7L);
        when(interviewMapper.selectById(101L)).thenReturn(interview);
        Report report = new Report();
        report.setId(91L);
        report.setInterviewId(101L);
        report.setStatus(0);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(questionMapper.selectCount(any())).thenReturn(5L);
        ReportService service = new ReportService(reportMapper, interviewMapper, questionMapper,
                mock(EvaluationMapper.class), mock(UserMapper.class), currentUser, mock(DeepSeekGateway.class),
                mock(CompanyAccessService.class));

        ReportDtos.ReportDetail detail = service.detail(101L);

        assertEquals(91L, detail.id());
        assertEquals(101L, detail.interviewId());
    }

    @Test
    void pageForAdminUsesDatabasePageAndBatchLoadsOnlyCurrentPage() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        ReportService service = new ReportService(reportMapper, interviewMapper, questionMapper, evaluationMapper,
                userMapper, currentUser, mock(DeepSeekGateway.class), mock(CompanyAccessService.class));

        Report report = new Report();
        report.setId(91L);
        report.setInterviewId(101L);
        report.setTotalScore(new java.math.BigDecimal("86.5"));
        Page<Report> page = new Page<>(1, 20);
        page.setRecords(List.of(report));
        page.setTotal(1);
        when(reportMapper.selectPage(any(Page.class), any())).thenReturn(page);
        Interview interview = new Interview();
        interview.setId(101L);
        interview.setCandidateId(7L);
        interview.setTitle("Java 后端面试");
        interview.setScheduledAt(LocalDateTime.now());
        when(interviewMapper.selectBatchIds(any())).thenReturn(List.of(interview));
        UserAccount candidate = new UserAccount();
        candidate.setId(7L);
        candidate.setRealName("刘洋");
        candidate.setUsername("candidate_liu");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(candidate));

        var result = service.pageForAdmin(new ReportDtos.ReportQuery(1L, 20L, null));

        assertEquals(1, result.total());
        ReportListItem item = result.records().get(0);
        assertEquals("Java 后端面试", item.interviewTitle());
        assertEquals("刘洋", item.candidateName());
        verify(reportMapper).selectPage(any(Page.class), any());
        verify(reportMapper, never()).selectList(any());
        verify(interviewMapper).selectBatchIds(any());
        verify(interviewMapper, never()).selectList(any());
        verify(userMapper).selectBatchIds(any());
        verify(userMapper, never()).selectList(any());
    }

    @Test
    void keywordSearchFindsCandidateBeforeDatabasePagination() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        ReportService service = new ReportService(reportMapper, interviewMapper, questionMapper, evaluationMapper,
                userMapper, currentUser, mock(DeepSeekGateway.class), mock(CompanyAccessService.class));
        UserAccount candidate = new UserAccount();
        candidate.setId(7L);
        when(userMapper.selectList(any())).thenReturn(List.of(candidate));
        Interview matchingInterview = new Interview();
        matchingInterview.setId(101L);
        matchingInterview.setCandidateId(7L);
        when(interviewMapper.selectList(any())).thenReturn(List.of(matchingInterview));
        Report report = new Report();
        report.setId(91L);
        report.setInterviewId(101L);
        Page<Report> page = new Page<>(1, 20);
        page.setRecords(List.of(report));
        page.setTotal(1);
        when(reportMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(interviewMapper.selectBatchIds(any())).thenReturn(List.of(matchingInterview));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(candidate));

        var result = service.pageForAdmin(new ReportDtos.ReportQuery(1L, 20L, "candidate_liu"));

        assertEquals(1, result.total());
        verify(reportMapper).selectPage(any(Page.class), any());
        verify(userMapper).selectList(any());
        verify(interviewMapper).selectList(any());
    }
}
