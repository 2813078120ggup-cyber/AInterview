package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.utils.TokenHashUtils;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

class VerificationCodeServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private VerificationCodeProperties properties;
    private VerificationCodeService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(values.increment(anyString())).thenReturn(1L);
        properties = new VerificationCodeProperties();
        properties.setMailFrom("noreply@example.com");
        properties.setTtl(Duration.ofMinutes(5));
        properties.setCooldown(Duration.ofSeconds(60));
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
        when(mailProvider.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        service = new VerificationCodeService(redis, RestClient.builder(), mailProvider, properties);
    }

    @Test
    void changeCodeKeysContainUserPurposeAndTargetDigestAndDailyLimitIsUserScoped() {
        service.sendChangeCode(11L, "CHANGE_EMAIL", "new@example.com");

        String prefix = "auth:account-code:11:CHANGE_EMAIL:" + TokenHashUtils.sha256("new@example.com");
        verify(values).setIfAbsent(prefix + ":cooldown", "1", 60L, java.util.concurrent.TimeUnit.SECONDS);
        verify(values).setIfAbsent("auth:account-code:11:CHANGE_EMAIL:" + TokenHashUtils.sha256("new@example.com") + ":cooldown", "1", 60L, java.util.concurrent.TimeUnit.SECONDS);
        verify(values).increment("auth:account-code-daily:11:CHANGE_EMAIL:" + java.time.LocalDate.now());
        verify(values).set(eq(prefix + ":code"), anyString(), eq(300L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void crossPurposeVerificationCannotReuseAnotherPurposeCode() {
        when(values.get(anyString())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verifyChangeCode(11L, "CHANGE_PHONE", "13800000000", "123456"));

        assertEquals(400, exception.getStatus().value());
        verify(values).get("auth:account-code:11:CHANGE_PHONE:" + TokenHashUtils.sha256("13800000000") + ":code");
    }

    @Test
    void invalidProviderConfigurationReturnsUnavailableAndDoesNotLeaveCooldown() {
        VerificationCodeProperties unavailable = new VerificationCodeProperties();
        unavailable.setSmsAppCode(null);
        unavailable.setCooldown(Duration.ofSeconds(60));
        unavailable.setTtl(Duration.ofMinutes(5));
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
        when(mailProvider.getIfAvailable()).thenReturn(null);
        VerificationCodeService unavailableService = new VerificationCodeService(redis, RestClient.builder(), mailProvider, unavailable);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> unavailableService.sendChangeCode(11L, "CHANGE_PHONE", "13800000000"));

        assertEquals(503, exception.getStatus().value());
        verify(redis).delete("auth:account-code:11:CHANGE_PHONE:" + TokenHashUtils.sha256("13800000000") + ":cooldown");
    }

    @Test
    void passwordResetUsesDedicatedUserPurposeAndTargetDigestKey() {
        service.sendPasswordResetCode(11L, "email", "candidate@example.com", true);

        String prefix = "auth:password-reset-code:11:PASSWORD_RESET:email:"
                + TokenHashUtils.sha256("candidate@example.com");
        verify(values).setIfAbsent(prefix + ":cooldown", "1", 60L, java.util.concurrent.TimeUnit.SECONDS);
        verify(values).set(eq(prefix + ":code"), anyString(), eq(300L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void passwordResetCannotReuseLoginOrContactCode() {
        when(values.get(anyString())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verifyPasswordResetCode(11L, "sms", "13800000000", "123456"));

        assertEquals(400, exception.getStatus().value());
        verify(values).get("auth:password-reset-code:11:PASSWORD_RESET:sms:"
                + TokenHashUtils.sha256("13800000000") + ":code");
    }

    @Test
    void passwordResetFailureLimitDeletesCode() {
        properties.setMaxPasswordResetVerifyFailures(2);
        when(values.get(anyString())).thenReturn("654321");
        when(values.increment(anyString())).thenReturn(2L);
        String prefix = "auth:password-reset-code:11:PASSWORD_RESET:sms:"
                + TokenHashUtils.sha256("13800000000");

        assertThrows(BusinessException.class,
                () -> service.verifyPasswordResetCode(11L, "sms", "13800000000", "123456"));

        verify(redis).delete(prefix + ":code");
        verify(redis).delete(prefix + ":failures");
    }

    @Test
    void passwordResetSendHonorsCooldownBeforeProviderDelivery() {
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendPasswordResetCode(11L, "email", "candidate@example.com", true));

        assertEquals(400, exception.getStatus().value());
        assertEquals("验证码发送过于频繁，请稍后再试", exception.getMessage());
    }
}
