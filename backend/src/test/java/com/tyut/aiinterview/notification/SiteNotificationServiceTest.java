package com.tyut.aiinterview.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.domain.SiteNotification;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.mapper.SiteNotificationMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SiteNotificationServiceTest {
    private final SiteNotificationMapper mapper = mock(SiteNotificationMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final SiteNotificationService service = new SiteNotificationService(
            mapper, reportMapper, currentUser, mock(ApplicationEventPublisher.class));

    @Test
    void candidateNotificationsResolveToCandidateDestinations() {
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        Report report = report(41L, 401L);
        when(reportMapper.selectBatchIds(List.of(41L))).thenReturn(List.of(report));
        page(List.of(notification(1L, "JOB_APPLICATION", 11L), notification(2L, "INTERVIEW", 21L),
                notification(3L, "REPORT", 41L), notification(4L, "FEEDBACK_TICKET", 51L),
                notification(5L, "USER", 7L)));

        List<NotificationDtos.Notification> records = service.page(new NotificationDtos.Query(1L, 20L)).records();

        assertEquals("/applications?applicationId=11", records.get(0).actionPath());
        assertEquals("/candidate/interviews/21/room", records.get(1).actionPath());
        assertEquals("/candidate/interviews/401/report", records.get(2).actionPath());
        assertEquals("/candidate/tickets/51", records.get(3).actionPath());
        assertEquals("/candidate/settings/security", records.get(4).actionPath());
    }

    @Test
    void companyNotificationsResolveToCompanyDestinations() {
        when(currentUser.id()).thenReturn(8L);
        when(currentUser.hasCompanyRole()).thenReturn(true);
        Report report = report(42L, 402L);
        when(reportMapper.selectBatchIds(List.of(42L))).thenReturn(List.of(report));
        page(List.of(notification(1L, "JOB_APPLICATION", 12L), notification(2L, "INTERVIEW", 22L),
                notification(3L, "REPORT", 42L), notification(4L, "FEEDBACK_TICKET", 52L),
                notification(5L, "USER", 8L)));

        List<NotificationDtos.Notification> records = service.page(new NotificationDtos.Query(1L, 20L)).records();

        assertEquals("/company/applications/12", records.get(0).actionPath());
        assertEquals("/company/interviews/AI-22", records.get(1).actionPath());
        assertEquals("/company/interviews/AI-402", records.get(2).actionPath());
        assertNull(records.get(3).actionPath());
        assertEquals("/company/account/security", records.get(4).actionPath());
    }

    @Test
    void adminNotificationsResolveToAdminDestinations() {
        when(currentUser.id()).thenReturn(9L);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        Report report = report(43L, 403L);
        when(reportMapper.selectBatchIds(List.of(43L))).thenReturn(List.of(report));
        page(List.of(notification(1L, "JOB_APPLICATION", 13L), notification(2L, "INTERVIEW", 23L),
                notification(3L, "REPORT", 43L), notification(4L, "FEEDBACK_TICKET", 53L),
                notification(5L, "USER", 9L)));

        List<NotificationDtos.Notification> records = service.page(new NotificationDtos.Query(1L, 20L)).records();

        assertEquals("/admin/recruitment/applications/13", records.get(0).actionPath());
        assertEquals("/admin/interviews/23/review", records.get(1).actionPath());
        assertEquals("/admin/interviews/403/review", records.get(2).actionPath());
        assertEquals("/admin/tickets/53", records.get(3).actionPath());
        assertEquals("/admin/account/security", records.get(4).actionPath());
    }

    private void page(List<SiteNotification> records) {
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<SiteNotification> result = invocation.getArgument(0);
            result.setRecords(records);
            result.setTotal(records.size());
            return result;
        });
    }

    private static SiteNotification notification(Long id, String businessType, Long businessId) {
        SiteNotification item = new SiteNotification();
        item.setId(id);
        item.setRecipientId(7L);
        item.setNotificationType("TEST");
        item.setTitle("测试通知");
        item.setContent("测试内容");
        item.setBusinessType(businessType);
        item.setBusinessId(businessId);
        return item;
    }

    private static Report report(Long id, Long interviewId) {
        Report report = new Report();
        report.setId(id);
        report.setInterviewId(interviewId);
        return report;
    }
}
