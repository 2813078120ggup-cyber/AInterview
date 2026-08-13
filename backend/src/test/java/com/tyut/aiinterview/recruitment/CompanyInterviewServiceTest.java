package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.InterviewStatusHistory;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.OfflineInterview;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.CompanyInterviewMapper;
import com.tyut.aiinterview.mapper.CompanyInterviewRow;
import com.tyut.aiinterview.mapper.InterviewStatusHistoryMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompanyInterviewServiceTest {
    private final CompanyAccessService companyAccess = org.mockito.Mockito.mock(CompanyAccessService.class);
    private final CompanyInterviewMapper interviewMapper = org.mockito.Mockito.mock(CompanyInterviewMapper.class);
    private final OfflineInterviewMapper offlineMapper = org.mockito.Mockito.mock(OfflineInterviewMapper.class);
    private final InterviewStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(InterviewStatusHistoryMapper.class);
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final AiTaskMapper aiTaskMapper = org.mockito.Mockito.mock(AiTaskMapper.class);
    private final AiTaskService aiTaskService = org.mockito.Mockito.mock(AiTaskService.class);
    private final SiteNotificationService notificationService = org.mockito.Mockito.mock(SiteNotificationService.class);
    private final CurrentUser currentUser = org.mockito.Mockito.mock(CurrentUser.class);
    private final CompanyInterviewService service = new CompanyInterviewService(companyAccess, interviewMapper,
            offlineMapper, historyMapper, userMapper, aiTaskMapper, aiTaskService, notificationService, currentUser);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> type : List.of(Company.class, OfflineInterview.class, InterviewStatusHistory.class,
                JobApplication.class, AiTask.class, UserAccount.class)) {
            if (TableInfoHelper.getTableInfo(type) == null) TableInfoHelper.initTableInfo(assistant, type);
        }
        when(currentUser.id()).thenReturn(88L);
        when(companyAccess.isRestrictedInterviewer()).thenReturn(false);
    }

    @Test
    void pageAlwaysUsesTheCurrentCompanyAndReturnsServerTime() {
        when(companyAccess.requirePermission("application:read")).thenReturn(200L);
        when(interviewMapper.count(any(), any(), any(Boolean.class), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        CompanyInterviewRow row = row("AI-11", "AI", 11L, null, 301L, 200L, "SCHEDULED");
        when(interviewMapper.selectPage(any(), any(), any(Boolean.class), any(), any(), any(), any(), any(), any(),
                any(), any(), any(Integer.class), any(Integer.class))).thenReturn(List.of(row));

        CompanyInterviewDtos.Page result = service.page(new CompanyInterviewDtos.Query(1, 20, "ALL", null,
                "", null, "SOONEST"));

        assertEquals(1L, result.total());
        assertNotNull(result.serverNow());
        verify(interviewMapper).count(200L, 88L, false, null, null, null, null, "ALL", null, null);
        verify(interviewMapper).selectPage(200L, 88L, false, null, null, null, null, "ALL", null, null,
                "SOONEST", 0, 20);
    }

    @Test
    void crossCompanyDetailIsHiddenAsNotFound() {
        when(companyAccess.requirePermission("application:read")).thenReturn(200L);
        when(companyAccess.requireCompanyId()).thenReturn(200L);
        when(interviewMapper.selectAi(200L, 88L, false, 11L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.detail("AI-11"));

        assertEquals(404, exception.getStatus().value());
        verify(companyAccess, never()).requireApplication(any());
    }

    @Test
    void cancelWritesHistoryAndCandidateNotificationWithinTheCompanyScope() {
        when(companyAccess.requirePermission("interview:review")).thenReturn(100L);
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireCompanyId()).thenReturn(100L);
        CompanyInterviewRow before = row("OFFLINE-21", "OFFLINE", null, 21L, 301L, 100L, "SCHEDULED");
        CompanyInterviewRow after = row("OFFLINE-21", "OFFLINE", null, 21L, 301L, 100L, "CANCELLED");
        when(interviewMapper.selectOffline(100L, 88L, false, 21L)).thenReturn(before, after);
        when(companyAccess.requireApplication(301L)).thenReturn(application(301L, 100L));
        when(offlineMapper.selectById(21L)).thenReturn(offline("SCHEDULED"));
        when(offlineMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.selectList(any())).thenReturn(List.of());

        CompanyInterviewDtos.Detail result = service.cancel("OFFLINE-21",
                new CompanyInterviewDtos.ActionRequest("候选人时间冲突"));

        assertEquals("CANCELLED", result.item().status());
        ArgumentCaptor<InterviewStatusHistory> history = ArgumentCaptor.forClass(InterviewStatusHistory.class);
        verify(historyMapper).insert(history.capture());
        assertEquals("OFFLINE", history.getValue().getInterviewKind());
        assertEquals("SCHEDULED", history.getValue().getFromStatus());
        assertEquals("CANCELLED", history.getValue().getToStatus());
        assertEquals("SENT", history.getValue().getNotificationStatus());
        verify(notificationService).create(301L, "INTERVIEW_CANCELLED", "线下面试已取消",
                "你收到的线下面试安排已取消，请关注后续通知。", "JOB_APPLICATION", 301L,
                "company-interview-OFFLINE-21-cancelled");
    }

    @Test
    void eachDistinctRescheduleHasItsOwnIdempotentNotificationKey() {
        when(companyAccess.requirePermission("interview:review")).thenReturn(100L);
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireCompanyId()).thenReturn(100L);
        CompanyInterviewRow before = row("OFFLINE-21", "OFFLINE", null, 21L, 301L, 100L, "SCHEDULED");
        CompanyInterviewRow after = row("OFFLINE-21", "OFFLINE", null, 21L, 301L, 100L, "SCHEDULED");
        when(interviewMapper.selectOffline(100L, 88L, false, 21L)).thenReturn(before, after);
        when(companyAccess.requireApplication(301L)).thenReturn(application(301L, 100L));
        when(offlineMapper.selectById(21L)).thenReturn(offline("SCHEDULED"));
        when(offlineMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime next = LocalDateTime.now().plusDays(1);

        service.reschedule("OFFLINE-21", new CompanyInterviewDtos.RescheduleRequest(next, 45, "会议室调整"));

        verify(notificationService).create(301L, "INTERVIEW_RESCHEDULED", "面试安排已更新",
                "你的线下面试已改期至 " + next.toString().replace('T', ' ') + "。", "JOB_APPLICATION", 301L,
                "company-interview-OFFLINE-21-rescheduled-" + next);
    }

    @Test
    void concurrentCancelReturnsConflictAndDoesNotWriteSideEffects() {
        when(companyAccess.requirePermission("interview:review")).thenReturn(100L);
        when(companyAccess.requireCompanyId()).thenReturn(100L);
        CompanyInterviewRow row = row("OFFLINE-21", "OFFLINE", null, 21L, 301L, 100L, "SCHEDULED");
        when(interviewMapper.selectOffline(100L, 88L, false, 21L)).thenReturn(row);
        when(offlineMapper.selectById(21L)).thenReturn(offline("SCHEDULED"));
        when(offlineMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.cancel("OFFLINE-21", new CompanyInterviewDtos.ActionRequest("重复提交")));

        assertEquals(409, exception.getStatus().value());
        verify(historyMapper, never()).insert(any(InterviewStatusHistory.class));
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    private CompanyInterviewRow row(String activityId, String kind, Long interviewId, Long offlineId,
                                    Long applicationId, Long companyId, String status) {
        return new CompanyInterviewRow(kind, activityId, interviewId, offlineId, applicationId, companyId, 31L,
                "Java 工程师", 301L, "候选人", "candidate@example.com", "13800000000",
                "OFFLINE".equals(kind) ? "ONSITE" : "AI", null, status, LocalDateTime.now().plusHours(2),
                60, "上海", null, "HR", "13800000001", "备注", "UNDER_REVIEW", "SENT",
                LocalDateTime.now(), null);
    }

    private OfflineInterview offline(String status) {
        OfflineInterview item = new OfflineInterview();
        item.setId(21L);
        item.setApplicationId(301L);
        item.setCompanyId(100L);
        item.setCandidateId(301L);
        item.setStatus(status);
        item.setScheduledAt(LocalDateTime.now().plusHours(2));
        item.setDurationMinutes(60);
        return item;
    }

    private JobApplication application(Long id, Long companyId) {
        JobApplication item = new JobApplication();
        item.setId(id);
        item.setCompanyId(companyId);
        item.setCandidateId(301L);
        item.setPositionId(31L);
        return item;
    }
}
