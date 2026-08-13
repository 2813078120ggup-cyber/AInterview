package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.auth.SecurityNotificationService;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountPasswordServiceTest {
    @Mock private AccountService accountService;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private SecurityNotificationService notificationService;
    @Mock private OperationAuditService auditService;
    private AccountPasswordService service;
    private UserAccount current;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AccountPasswordService(accountService, userMapper, passwordEncoder, refreshTokenService,
                jwtTokenService, notificationService, auditService);
        current = user(2, "current-hash");
        when(accountService.requireCurrentUser()).thenReturn(current);
    }

    @Test
    void correctCurrentPasswordRotatesCurrentSessionAndInvalidatesOldCredentials() {
        UserAccount latest = user(3, "new-hash");
        when(passwordEncoder.matches("Current123!", "current-hash")).thenReturn(true);
        when(passwordEncoder.matches("Next12345!", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("Next12345!")).thenReturn("new-hash");
        when(refreshTokenService.rotateForUser(eq("old-refresh"), eq(11L), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                new RefreshTokenService.IssuedToken(11L, "new-refresh", "session-a"));
        when(userMapper.updatePasswordAndSecurityVersion(11L, "new-hash", 2)).thenReturn(1);
        when(userMapper.selectById(11L)).thenReturn(latest);
        when(jwtTokenService.createToken(11L, "candidate", 3, "session-a")).thenReturn("new-access");

        AccountDtos.ChangePasswordResponse response = service.change(
                new AccountDtos.ChangePasswordRequest("Current123!", "Next12345!", "old-refresh"),
                "127.0.0.1", "agent");

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        verify(refreshTokenService).revokeOtherSessions(11L, "session-a", "PASSWORD_CHANGED");
        verify(notificationService).notifyPasswordChanged(latest);
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeCredentials() {
        when(passwordEncoder.matches("Wrong123!", "current-hash")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.change(
                new AccountDtos.ChangePasswordRequest("Wrong123!", "Next12345!", "old-refresh"),
                "127.0.0.1", "agent"));

        assertEquals(403, exception.getStatus().value());
        verify(refreshTokenService, never()).rotateForUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(userMapper, never()).updatePasswordAndSecurityVersion(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void samePasswordIsRejectedBeforeRefreshTokenRotation() {
        when(passwordEncoder.matches("Current123!", "current-hash")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.change(
                new AccountDtos.ChangePasswordRequest("Current123!", "Current123!", "old-refresh"),
                "127.0.0.1", "agent"));

        assertEquals(400, exception.getStatus().value());
        verify(refreshTokenService, never()).rotateForUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private UserAccount user(int securityVersion, String passwordHash) {
        UserAccount user = new UserAccount();
        user.setId(11L);
        user.setUsername("candidate");
        user.setPasswordHash(passwordHash);
        user.setStatus(1);
        user.setSecurityVersion(securityVersion);
        return user;
    }
}
