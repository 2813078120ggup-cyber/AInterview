package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AuthControllerCaptchaTest {
    private final AuthService authService = mock(AuthService.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final ImageCaptchaService imageCaptchaService = mock(ImageCaptchaService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, verificationCodeService, imageCaptchaService,
                passwordResetService, currentUser);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
    }

    @Test
    void exposesImageChallengeContract() {
        when(imageCaptchaService.issue("PASSWORD_LOGIN", "203.0.113.10"))
                .thenReturn(new ImageCaptchaService.ChallengeResult("challenge", "data:image/png;base64,eA==", 120));

        var challenge = controller.captchaChallenge(new AuthDtos.CaptchaChallengeRequest("PASSWORD_LOGIN"), request);

        assertEquals("challenge", challenge.data().challengeId());
        assertEquals("data:image/png;base64,eA==", challenge.data().imageDataUrl());
        assertEquals(120, challenge.data().expiresInSeconds());
    }

    @Test
    void consumesImageChallengeBeforeSendingLoginCode() {
        AuthDtos.SendLoginCodeRequest body = new AuthDtos.SendLoginCodeRequest(
                "sms", "13800138000", "challenge", "ABCD");

        controller.sendLoginCode(body, request);

        InOrder order = inOrder(imageCaptchaService, authService);
        order.verify(imageCaptchaService).consumeChallenge("challenge", "ABCD", "LOGIN_CODE_SEND", "203.0.113.10");
        order.verify(authService).sendLoginCode(body);
    }

    @Test
    void consumesImageChallengeBeforeCallingAuthService() {
        AuthDtos.LoginRequest body = new AuthDtos.LoginRequest("candidate", "Password123", "challenge", "ABCD");
        when(authService.login(eq(body), eq("203.0.113.10"), eq(null)))
                .thenReturn(new AuthDtos.LoginResponse("access", "refresh", null));

        controller.login(body, request);

        InOrder order = inOrder(imageCaptchaService, authService);
        order.verify(imageCaptchaService).consumeChallenge("challenge", "ABCD", "PASSWORD_LOGIN", "203.0.113.10");
        order.verify(authService).login(body, "203.0.113.10", null);
    }

    @Test
    void consumesImageChallengeBeforeSendingPasswordResetCode() {
        AuthDtos.PasswordResetCodeRequest body = new AuthDtos.PasswordResetCodeRequest(
                "email", "candidate@example.com", "challenge", "ABCD");

        controller.sendPasswordResetCode(body, request);

        InOrder order = inOrder(imageCaptchaService, passwordResetService);
        order.verify(imageCaptchaService).consumeChallenge(
                "challenge", "ABCD", "PASSWORD_RESET_CODE_SEND", "203.0.113.10");
        order.verify(passwordResetService).sendCode(body);
    }

    @Test
    void delegatesPublicCompanyRegistrationWithoutAnAuthenticatedUser() {
        AuthDtos.CompanyRegisterRequest body = new AuthDtos.CompanyRegisterRequest(
                "hr_admin", "Password123", "林晓雯", "hr@example.com", "13800138000", "123456",
                "星云科技", "星云", "人工智能", "500-999人", "北京", "https://example.com",
                "企业简介", "李明", null);
        AuthDtos.CompanyRegisterResponse expected = new AuthDtos.CompanyRegisterResponse(
                100L, "ENT-TEST", new AuthDtos.UserProfile(200L, "hr_admin", "林晓雯", java.util.List.of("COMPANY_ADMIN"), 100L));
        when(authService.registerCompany(body)).thenReturn(expected);

        var response = controller.registerCompany(body);

        assertEquals(100L, response.data().companyId());
        verify(authService).registerCompany(body);
    }
}
