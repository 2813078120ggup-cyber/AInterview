package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Evaluation;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.recording.InterviewRecordingService;
import com.tyut.aiinterview.report.ReportDtos;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanyReportReviewServiceTest {
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
    private final InterviewAnswerMapper answerMapper = mock(InterviewAnswerMapper.class);
    private final EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final InterviewRecordingService recordingService = mock(InterviewRecordingService.class);
    private final AiTaskService aiTaskService = mock(AiTaskService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private CompanyReportReviewService service;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> type : List.of(AiTask.class, Evaluation.class, Interview.class, InterviewAnswer.class,
                InterviewQuestion.class, JobApplication.class, Report.class)) {
            if (TableInfoHelper.getTableInfo(type) == null) TableInfoHelper.initTableInfo(assistant, type);
        }
        service = new CompanyReportReviewService(companyAccess, reportMapper, questionMapper, answerMapper,
                evaluationMapper, taskMapper, recordingService, aiTaskService, currentUser, new ObjectMapper());
        when(companyAccess.requireAnyPermission("interview:review", "report:read")).thenReturn(1L);
        when(currentUser.hasPermission("interview:review")).thenReturn(true);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(answerMapper.selectList(any())).thenReturn(List.of());
        when(evaluationMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(recordingService.companyView(101L)).thenReturn(null);
    }

    @Test
    void exposesInternalReadyReportWithoutPublishingIt() {
        JobApplication application = application();
        Interview interview = interview();
        Report report = report(0);
        when(companyAccess.requireApplication(11L)).thenReturn(application);
        when(companyAccess.requireInterviewForApplication(application)).thenReturn(interview);
        when(reportMapper.selectOne(any())).thenReturn(report);

        ReportDtos.CompanyReportDetail detail = service.companyDetail(11L);

        assertEquals("READY", detail.reportStatus());
        assertEquals(0, detail.status());
        assertFalse(detail.canRetry());
        assertEquals(101L, detail.interviewId());
        verify(reportMapper, never()).updateById(any(Report.class));
    }

    @Test
    void companyBoundaryStopsCrossTenantLookupBeforeReportData() {
        when(companyAccess.requireApplication(11L)).thenThrow(BusinessException.notFound("申请不存在"));

        assertThrows(BusinessException.class, () -> service.companyDetail(11L));

        verify(reportMapper, never()).selectOne(any());
        verify(questionMapper, never()).selectList(any());
        verify(recordingService, never()).companyView(any());
    }

    @Test
    void failedGenerationIsReadableAndRetryableWithoutLeakingInternalError() {
        JobApplication application = application();
        Interview interview = interview();
        interview.setStatus(Interview.FAILED);
        AiTask task = new AiTask();
        task.setInterviewId(101L);
        task.setTaskType(AiTaskService.AUTO_EVALUATION);
        task.setStatus("FAILED");
        task.setAttempts(3);
        task.setErrorMessage("provider key and stack trace");
        when(companyAccess.requireApplication(11L)).thenReturn(application);
        when(companyAccess.requireInterviewForApplication(application)).thenReturn(interview);
        when(taskMapper.selectOne(any())).thenReturn(task);

        ReportDtos.CompanyReportDetail detail = service.companyDetail(11L);

        assertEquals("FAILED", detail.reportStatus());
        assertTrue(detail.canRetry());
        assertEquals("AI 报告生成失败，请重试。", detail.taskMessage());
        assertFalse(detail.toString().contains("provider key"));
    }

    @Test
    void publishesOnlyThroughExplicitCompanyAction() {
        JobApplication application = application();
        Interview interview = interview();
        Report report = report(0);
        when(companyAccess.requireApplication(11L)).thenReturn(application);
        when(companyAccess.requireInterviewForApplication(application)).thenReturn(interview);
        when(reportMapper.selectOne(any())).thenReturn(report);

        ReportDtos.CompanyReportDetail detail = service.publish(11L);

        assertEquals("PUBLISHED", detail.reportStatus());
        assertEquals(1, report.getStatus());
        assertNotNull(report.getPublishedAt());
        verify(reportMapper).updateById(report);
    }

    @Test
    void returnsStructuredAnswersFollowUpsAndScoresWithoutRawJson() {
        JobApplication application = application();
        Interview interview = interview();
        InterviewQuestion question = new InterviewQuestion();
        question.setId(201L);
        question.setInterviewId(101L);
        question.setSequenceNo(1);
        question.setQuestionSnapshot("{\"content\":\"请说明缓存失效策略\",\"questionType\":\"subjective\"}");
        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(301L);
        answer.setInterviewQuestionId(201L);
        answer.setAnswerContent("先删缓存，再更新数据库并处理并发窗口。");
        Evaluation evaluation = new Evaluation();
        evaluation.setInterviewQuestionId(201L);
        evaluation.setOverallScore(new BigDecimal("88"));
        evaluation.setProfessionalScore(new BigDecimal("90"));
        evaluation.setSource("ai");
        AiTask followUp = new AiTask();
        followUp.setInterviewId(101L);
        followUp.setAnswerId(301L);
        followUp.setTaskType(AiTaskService.FOLLOW_UP);
        followUp.setStatus("SUCCESS");
        followUp.setOutputPayload("{\"followUp\":\"如何处理缓存击穿？\",\"generationRequestId\":\"secret\"}");
        when(companyAccess.requireApplication(11L)).thenReturn(application);
        when(companyAccess.requireInterviewForApplication(application)).thenReturn(interview);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(answerMapper.selectList(any())).thenReturn(List.of(answer));
        when(evaluationMapper.selectList(any())).thenReturn(List.of(evaluation));
        when(taskMapper.selectList(any())).thenReturn(List.of(followUp));

        ReportDtos.CompanyReportDetail detail = service.companyDetail(11L);

        assertEquals("请说明缓存失效策略", detail.questionReviews().get(0).question());
        assertEquals("先删缓存，再更新数据库并处理并发窗口。", detail.questionReviews().get(0).answer());
        assertEquals("如何处理缓存击穿？", detail.questionReviews().get(0).followUps().get(0));
        assertEquals(new BigDecimal("88"), detail.questionReviews().get(0).evaluation().overallScore());
        assertFalse(detail.toString().contains("generationRequestId"));
    }

    private JobApplication application() {
        JobApplication application = new JobApplication();
        application.setId(11L);
        application.setCompanyId(1L);
        application.setCandidateId(7L);
        application.setPositionId(9L);
        application.setInterviewId(101L);
        return application;
    }

    private Interview interview() {
        Interview interview = new Interview();
        interview.setId(101L);
        interview.setCandidateId(7L);
        interview.setPositionId(9L);
        interview.setStatus(Interview.REPORT_READY);
        return interview;
    }

    private Report report(int status) {
        Report report = new Report();
        report.setId(501L);
        report.setInterviewId(101L);
        report.setStatus(status);
        report.setTotalScore(new BigDecimal("86"));
        return report;
    }
}
