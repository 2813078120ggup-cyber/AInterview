package com.tyut.aiinterview.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.account.AccountNotificationPreferenceService;
import com.tyut.aiinterview.account.CandidateNotificationEvent;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateNotificationDeliveryListenerTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountNotificationPreferenceService preferenceService = mock(AccountNotificationPreferenceService.class);
    private final CandidateNotificationDeliveryExecutor executor = mock(CandidateNotificationDeliveryExecutor.class);
    private final CandidateNotificationDeliveryListener listener = new CandidateNotificationDeliveryListener(
            userMapper, preferenceService, executor);
    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setId(7L);
        user.setStatus(1);
        user.setEmail("candidate@example.com");
        user.setPhone("13800000000");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.hasActiveRole(7L, "CANDIDATE")).thenReturn(true);
    }

    @Test
    void actualDeliveryReadsCandidatePreference() {
        var event = event(CandidateNotificationEvent.REPORT_PUBLISHED);
        when(preferenceService.deliveryPreference(user, CandidateNotificationEvent.REPORT_PUBLISHED))
                .thenReturn(new AccountNotificationPreferenceService.DeliveryPreference(false, true, false));

        listener.deliver(event);

        verify(executor, never()).createSite(event);
        verify(executor).sendEmail(user, event);
        verify(executor, never()).sendSms(user, event);
    }

    @Test
    void providerFailureDoesNotEscapeToBusinessFlow() {
        var event = event(CandidateNotificationEvent.INTERVIEW_CREATED);
        when(preferenceService.deliveryPreference(user, CandidateNotificationEvent.INTERVIEW_CREATED))
                .thenReturn(new AccountNotificationPreferenceService.DeliveryPreference(true, true, false));
        doThrow(new IllegalStateException("provider down")).when(executor).sendEmail(user, event);

        assertDoesNotThrow(() -> listener.deliver(event));

        verify(executor).createSite(event);
        verify(executor).audit(event, "EMAIL", "FAILURE");
    }

    @Test
    void preferenceLookupFailureDoesNotEscapeAfterBusinessCommit() {
        var event = event(CandidateNotificationEvent.APPLICATION_STATUS_CHANGED);
        when(preferenceService.deliveryPreference(user, CandidateNotificationEvent.APPLICATION_STATUS_CHANGED))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> listener.deliver(event));

        verify(executor, never()).createSite(event);
    }

    @Test
    void nonPreferenceNotificationKeepsExistingSiteBehaviorWithoutExternalChannels() {
        CandidateNotificationRequested event = new CandidateNotificationRequested(7L, "FEEDBACK_TICKET_MESSAGE",
                null, "工单更新", "你有一条工单回复", "FEEDBACK_TICKET", 9L, "ticket-9");

        listener.deliver(event);

        verify(executor).createSite(event);
        verify(preferenceService, never()).deliveryPreference(user, null);
        verify(executor, never()).sendEmail(user, event);
        verify(executor, never()).sendSms(user, event);
    }

    private static CandidateNotificationRequested event(CandidateNotificationEvent event) {
        return new CandidateNotificationRequested(7L, event.name(), event, event.label(), event.description(),
                "JOB_APPLICATION", 11L, event.name().toLowerCase());
    }
}
