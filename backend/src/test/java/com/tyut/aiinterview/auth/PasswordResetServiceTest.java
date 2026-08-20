package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private CompanyMapper companyMapper;
    @Mock private VerificationCodeService codeService;
    @Mock private PasswordResetTicketService ticketService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SecurityNotificationService notificationService;
    @Mock private OperationAuditService auditService;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PasswordResetService(userMapper, companyMapper, codeService, ticketService, passwordEncoder,
                refreshTokenService, notificationService, auditService);
    }

    @Test
    void verifiedPhoneResetBumpsSecurityVersionAndRevokesAllSessions() {
        UserAccount user = user();
        UserAccount latest = user();
        latest.setSecurityVersion(5);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Next12345!", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("Next12345!")).thenReturn("new-hash");
        when(userMapper.updatePasswordAndSecurityVersion(11L, "new-hash", 4)).thenReturn(1);
        when(userMapper.selectById(11L)).thenReturn(latest);

        AuthDtos.PasswordResetResponse response = service.reset(new AuthDtos.PasswordResetRequest(
                "sms", "13800000000", "123456", "Next12345!"));

        assertEquals("密码已重置，全部设备会话已失效，请重新登录", response.sessionBehavior());
        verify(codeService).verifyPasswordResetCode(11L, "sms", "13800000000", "123456");
        verify(refreshTokenService).revokeAllSessions(11L, "PASSWORD_RESET");
        verify(notificationService).notifyPasswordChanged(latest);
    }

    @Test
    void unknownTargetGetsUniformCodeRequestResponseWithoutDelivery() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(codeService.sendPasswordResetCode(null, "email", "missing@example.com", false))
                .thenReturn(new VerificationCodeService.PasswordResetCodeResult(60, 300));

        AuthDtos.PasswordResetCodeResponse response = service.sendCode(
                new AuthDtos.PasswordResetCodeRequest("email", "missing@example.com"));

        assertEquals(true, response.accepted());
        assertEquals("若该联系方式可用于找回账户，验证码将发送至该联系方式", response.message());
    }

    @Test
    void existingAndUnknownTargetsExposeTheSamePublicCodeResponse() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user).thenReturn(null);
        when(codeService.sendPasswordResetCode(11L, "email", "candidate@example.com", true))
                .thenReturn(new VerificationCodeService.PasswordResetCodeResult(60, 300));
        when(codeService.sendPasswordResetCode(null, "email", "missing@example.com", false))
                .thenReturn(new VerificationCodeService.PasswordResetCodeResult(60, 300));

        AuthDtos.PasswordResetCodeResponse existing = service.sendCode(
                new AuthDtos.PasswordResetCodeRequest("email", "candidate@example.com"));
        AuthDtos.PasswordResetCodeResponse missing = service.sendCode(
                new AuthDtos.PasswordResetCodeRequest("email", "missing@example.com"));

        assertEquals(existing.accepted(), missing.accepted());
        assertEquals(existing.cooldownSeconds(), missing.cooldownSeconds());
        assertEquals(existing.expiresInSeconds(), missing.expiresInSeconds());
        assertEquals(existing.message(), missing.message());
    }

    @Test
    void verifiedCodeIssuesShortLivedResetTicket() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(ticketService.issue(11L, 4))
                .thenReturn(new PasswordResetTicketService.IssuedTicket("reset-ticket", 600));

        AuthDtos.PasswordResetVerifyResponse response = service.verifyCode(
                new AuthDtos.PasswordResetVerifyRequest("sms", "13800000000", "123456"));

        assertEquals("reset-ticket", response.resetToken());
        assertEquals(600, response.expiresInSeconds());
        verify(codeService).verifyPasswordResetCode(11L, "sms", "13800000000", "123456");
        verify(ticketService).issue(11L, 4);
    }

    @Test
    void unknownTargetCannotObtainResetTicket() {
        when(userMapper.selectOne(any())).thenReturn(null);
        org.mockito.Mockito.doThrow(BusinessException.badRequest("验证码错误或已过期"))
                .when(codeService).verifyPasswordResetCode(null, "email", "missing@example.com", "123456");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verifyCode(
                new AuthDtos.PasswordResetVerifyRequest("email", "missing@example.com", "123456")));

        assertEquals("验证码错误或已过期", exception.getMessage());
        verify(ticketService, never()).issue(any(), anyInt());
    }

    @Test
    void oneTimeTicketCompletesResetAndRevokesSessions() {
        UserAccount user = user();
        UserAccount latest = user();
        latest.setSecurityVersion(5);
        when(ticketService.consume("reset-ticket"))
                .thenReturn(new PasswordResetTicketService.VerifiedTicket(11L, 4));
        when(userMapper.selectById(11L)).thenReturn(user).thenReturn(latest);
        when(passwordEncoder.matches("Next12345!", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("Next12345!")).thenReturn("new-hash");
        when(userMapper.updatePasswordAndSecurityVersion(11L, "new-hash", 4)).thenReturn(1);

        AuthDtos.PasswordResetResponse response = service.complete(
                new AuthDtos.PasswordResetCompleteRequest("reset-ticket", "Next12345!"));

        assertEquals("密码已重置，全部设备会话已失效，请重新登录", response.sessionBehavior());
        verify(refreshTokenService).revokeAllSessions(11L, "PASSWORD_RESET");
        verify(notificationService).notifyPasswordChanged(latest);
    }

    @Test
    void wrongOrExpiredCodeDoesNotTouchPasswordOrSessions() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user);
        org.mockito.Mockito.doThrow(BusinessException.badRequest("验证码错误或已过期"))
                .when(codeService).verifyPasswordResetCode(11L, "email", "candidate@example.com", "000000");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.reset(
                new AuthDtos.PasswordResetRequest("email", "candidate@example.com", "000000", "Next12345!")));

        assertEquals(400, exception.getStatus().value());
        verify(userMapper, never()).updatePasswordAndSecurityVersion(any(), any(), any());
        verify(refreshTokenService, never()).revokeAllSessions(any(), any());
        verify(auditService).denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER", 11L, null,
                "密码重置验证码校验失败");
    }

    @Test
    void providerFailureIsControlled() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(codeService.sendPasswordResetCode(11L, "sms", "13800000000", true))
                .thenThrow(new RuntimeException("provider internals"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.sendCode(
                new AuthDtos.PasswordResetCodeRequest("sms", "13800000000")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("验证渠道暂不可用，请稍后重试", exception.getMessage());
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(11L);
        user.setUsername("candidate");
        user.setPasswordHash("current-hash");
        user.setPhone("13800000000");
        user.setEmail("candidate@example.com");
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setSecurityVersion(4);
        user.setStatus(1);
        return user;
    }
}
