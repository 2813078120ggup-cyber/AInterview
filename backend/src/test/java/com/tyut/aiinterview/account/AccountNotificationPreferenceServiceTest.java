package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.auth.VerificationCodeService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserNotificationPreference;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserNotificationPreferenceMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountNotificationPreferenceServiceTest {
    private final UserNotificationPreferenceMapper preferenceMapper = mock(UserNotificationPreferenceMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final AccountNotificationPreferenceService service = new AccountNotificationPreferenceService(
            preferenceMapper, userMapper, currentUser, verificationCodeService, auditService);
    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = activeCandidate(7L);
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        when(currentUser.id()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(preferenceMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void returnsSafeDefaultsWithoutCreatingRows() {
        user.setEmail("candidate@example.com");
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setPhone("13800000000");
        user.setPhoneVerifiedAt(LocalDateTime.now());
        when(verificationCodeService.isNotificationChannelAvailable("email")).thenReturn(true);
        when(verificationCodeService.isNotificationChannelAvailable("sms")).thenReturn(true);

        AccountNotificationPreferenceDtos.Preferences result = service.get();

        assertEquals(8, result.preferences().size());
        var application = preference(result, "APPLICATION_STATUS_CHANGED");
        assertTrue(application.siteEnabled());
        assertTrue(application.siteForced());
        var security = preference(result, "ACCOUNT_SECURITY");
        assertTrue(security.siteEnabled());
        assertTrue(security.emailEnabled());
        assertTrue(security.emailForced());
        var announcement = preference(result, "PLATFORM_ANNOUNCEMENT");
        assertTrue(announcement.siteEnabled());
        assertFalse(announcement.siteForced());
    }

    @Test
    void disablesUnverifiedOrUnavailableExternalChannelsAndReturnsReasons() {
        user.setEmail("candidate@example.com");
        user.setPhone("13800000000");
        when(verificationCodeService.isNotificationChannelAvailable(anyString())).thenReturn(false);

        AccountNotificationPreferenceDtos.Preferences result = service.get();

        assertFalse(result.channels().emailAvailable());
        assertEquals("邮箱尚未验证", result.channels().emailUnavailableReason());
        assertFalse(result.channels().smsAvailable());
        assertEquals("手机号尚未验证", result.channels().smsUnavailableReason());
        result.preferences().forEach(item -> {
            assertFalse(item.emailEnabled());
            assertFalse(item.smsEnabled());
        });
    }

    @Test
    void savesOnlyCurrentCandidateAndEnforcesMandatoryChannels() {
        user.setEmail("candidate@example.com");
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setPhone("13800000000");
        user.setPhoneVerifiedAt(LocalDateTime.now());
        when(verificationCodeService.isNotificationChannelAvailable(anyString())).thenReturn(true);
        UserNotificationPreference saved = preferenceRow(7L, CandidateNotificationEvent.ACCOUNT_SECURITY, 0,
                true, true, true);
        when(preferenceMapper.selectList(any())).thenReturn(List.of(), List.of(saved));

        var result = service.update(new AccountNotificationPreferenceDtos.UpdateRequest(List.of(
                new AccountNotificationPreferenceDtos.UpdatePreference("ACCOUNT_SECURITY", false, false, true, 0))));

        ArgumentCaptor<UserNotificationPreference> captor = ArgumentCaptor.forClass(UserNotificationPreference.class);
        verify(preferenceMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(1, captor.getValue().getSiteEnabled());
        assertEquals(1, captor.getValue().getEmailEnabled());
        assertEquals(1, captor.getValue().getSmsEnabled());
        assertTrue(preference(result, "ACCOUNT_SECURITY").emailEnabled());
        verify(auditService).success("ACCOUNT", "NOTIFICATION_PREFERENCES_UPDATED", "USER", 7L, null,
                "更新候选人通知偏好，共 1 个事件；未记录联系方式");
    }

    @Test
    void returnsConflictWhenOptimisticVersionChanged() {
        UserNotificationPreference row = preferenceRow(7L, CandidateNotificationEvent.PLATFORM_ANNOUNCEMENT, 3,
                false, false, false);
        when(preferenceMapper.selectList(any())).thenReturn(List.of(row));
        when(preferenceMapper.updateWithVersion(anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(
                new AccountNotificationPreferenceDtos.UpdateRequest(List.of(
                        new AccountNotificationPreferenceDtos.UpdatePreference(
                                "PLATFORM_ANNOUNCEMENT", true, false, false, 3)))));

        assertEquals(409, exception.getStatus().value());
    }

    @Test
    void rejectsHrAndAdministratorPreferenceManagement() {
        when(currentUser.hasRole("CANDIDATE")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, service::get);

        assertEquals(403, exception.getStatus().value());
    }

    private static AccountNotificationPreferenceDtos.Preference preference(
            AccountNotificationPreferenceDtos.Preferences result, String eventType) {
        return result.preferences().stream().filter(item -> eventType.equals(item.eventType())).findFirst().orElseThrow();
    }

    private static UserAccount activeCandidate(Long id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setStatus(1);
        return user;
    }

    private static UserNotificationPreference preferenceRow(Long userId, CandidateNotificationEvent event, int version,
                                                            boolean site, boolean email, boolean sms) {
        UserNotificationPreference row = new UserNotificationPreference();
        row.setId(1L);
        row.setUserId(userId);
        row.setEventType(event.name());
        row.setVersion(version);
        row.setSiteEnabled(site ? 1 : 0);
        row.setEmailEnabled(email ? 1 : 0);
        row.setSmsEnabled(sms ? 1 : 0);
        return row;
    }
}
