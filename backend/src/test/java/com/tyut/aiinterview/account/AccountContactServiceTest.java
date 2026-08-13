package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountContactServiceTest {
    @Mock private AccountService accountService;
    @Mock private UserMapper userMapper;
    @Mock private com.tyut.aiinterview.auth.VerificationCodeService codeService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private OperationAuditService auditService;
    private AccountContactService service;
    private UserAccount current;
    private UserAccount latest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AccountContactService(accountService, userMapper, codeService, passwordEncoder,
                refreshTokenService, jwtTokenService, auditService);
        current = user(11L, "old@example.com", "13800000000", 4, 2);
        latest = user(11L, "new@example.com", "13800001111", 5, 3);
        when(accountService.requireCurrentUser()).thenReturn(current);
        when(accountService.profile()).thenReturn(profile());
        when(passwordEncoder.matches("current-password", "hash")).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectById(11L)).thenReturn(latest);
        when(refreshTokenService.rotateForUser(eq("refresh-token"), eq(11L), any(), any(), any()))
                .thenReturn(new RefreshTokenService.IssuedToken(11L, "rotated-refresh", "session-a"));
        when(jwtTokenService.createToken(11L, "candidate", 3, "session-a")).thenReturn("access-token");
    }

    @Test
    void phoneChangeVerifiesPurposeUpdatesContactAndRotatesSessions() {
        when(userMapper.updatePhoneWithVerification(11L, "13800001111", 4)).thenReturn(1);

        AccountDtos.ContactChangeResponse response = service.change("PHONE",
                new AccountDtos.ChangeContactRequest("13800001111", "123456", "current-password", "refresh-token", 4),
                "127.0.0.1", "test-agent");

        assertEquals("access-token", response.accessToken());
        assertEquals("rotated-refresh", response.refreshToken());
        verify(codeService).verifyChangeCode(11L, "CHANGE_PHONE", "13800001111", "123456");
        verify(userMapper).updatePhoneWithVerification(11L, "13800001111", 4);
        verify(refreshTokenService).revokeOtherSessions(11L, "session-a", "CONTACT_CHANGED");
        verify(codeService).sendSecurityNotification("sms", "13800000000", "你的账户联系方式已变更。如非本人操作，请立即重新登录并联系平台管理员。");
        verify(codeService).sendSecurityNotification("sms", "13800001111", "你的账户联系方式已变更。如非本人操作，请立即重新登录并联系平台管理员。");
    }

    @Test
    void wrongPasswordDoesNotConsumeCodeOrRotateToken() {
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.change("EMAIL",
                new AccountDtos.ChangeContactRequest("new@example.com", "123456", "wrong", "refresh-token", 4),
                "127.0.0.1", "test-agent"));

        assertEquals(403, exception.getStatus().value());
        verify(codeService, never()).verifyChangeCode(any(), any(), any(), any());
        verify(refreshTokenService, never()).rotateForUser(any(), any(), any(), any(), any());
        verify(userMapper, never()).updateEmailWithVerification(any(), any(), any());
    }

    @Test
    void duplicateTargetUsesGenericConflictBeforeSessionRotation() {
        UserAccount other = user(22L, "taken@example.com", null, 1, 0);
        when(userMapper.selectOne(any())).thenReturn(other);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.change("EMAIL",
                new AccountDtos.ChangeContactRequest("taken@example.com", "123456", "current-password", "refresh-token", 4),
                "127.0.0.1", "test-agent"));

        assertEquals(409, exception.getStatus().value());
        assertEquals("该联系方式不可用", exception.getMessage());
        verify(codeService).verifyChangeCode(11L, "CHANGE_EMAIL", "taken@example.com", "123456");
        verify(refreshTokenService, never()).rotateForUser(any(), any(), any(), any(), any());
    }

    @Test
    void providerFailureDoesNotUndoSuccessfulContactUpdate() {
        when(userMapper.updateEmailWithVerification(11L, "new@example.com", 4)).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("provider down"))
                .when(codeService).sendSecurityNotification(any(), any(), any());

        AccountDtos.ContactChangeResponse response = service.change("EMAIL",
                new AccountDtos.ChangeContactRequest("new@example.com", "123456", "current-password", "refresh-token", 4),
                "127.0.0.1", "test-agent");

        assertEquals("rotated-refresh", response.refreshToken());
        verify(userMapper).updateEmailWithVerification(11L, "new@example.com", 4);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).failure(eq("ACCOUNT"), eq("CONTACT_CHANGE_NOTIFICATION_FAILED"),
                eq("USER"), eq(11L), any(), eq("联系方式变更安全通知发送失败"));
    }

    @Test
    void invalidTargetIsRejectedWithoutStoringSensitiveData() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.sendCode("EMAIL",
                new AccountDtos.ChangeCodeRequest("not-an-email")));
        assertEquals(400, exception.getStatus().value());
        verify(codeService, never()).sendChangeCode(any(), any(), any());
    }

    private UserAccount user(Long id, String email, String phone, int version, int securityVersion) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername("candidate");
        user.setPasswordHash("hash");
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        user.setVersion(version);
        user.setSecurityVersion(securityVersion);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setEmailVerifiedAt(LocalDateTime.now());
        return user;
    }

    private AccountDtos.AccountProfile profile() {
        return new AccountDtos.AccountProfile(11L, "candidate", "候选人", "CANDIDATE", 1, false,
                "new@example.com", "n***@example.com", true, "13800001111", "138****1111", true,
                java.util.List.of("PASSWORD", "SMS", "EMAIL"), null, null, 5);
    }
}
