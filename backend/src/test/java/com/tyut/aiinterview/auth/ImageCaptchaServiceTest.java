package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class ImageCaptchaServiceTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ImageCaptchaProperties properties = new ImageCaptchaProperties();
    private ImageCaptchaService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties.setFixedCode("ABCD");
        service = new ImageCaptchaService(redisTemplate, properties);
    }

    @Test
    void issuesPngDataUrlAndStoresOnlyHashedAnswerMetadata() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(120L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        ImageCaptchaService.ChallengeResult result = service.issue("password_login", "203.0.113.10");

        assertTrue(result.imageDataUrl().startsWith("data:image/png;base64,"));
        assertFalse(result.imageDataUrl().contains("ABCD"));
        byte[] imageBytes = Base64.getDecoder().decode(result.imageDataUrl().substring("data:image/png;base64,".length()));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(imageBytes)));
        org.mockito.ArgumentCaptor<String> stored = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(anyString(), stored.capture(), eq(120L), eq(TimeUnit.SECONDS));
        assertTrue(stored.getValue().contains("|PASSWORD_LOGIN|"));
        assertFalse(stored.getValue().contains("ABCD"));
        assertFalse(stored.getValue().contains("203.0.113.10"));
    }

    @Test
    void consumesChallengeOnceAndRejectsReplay() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L, 0L);

        service.consumeChallenge("challenge", "abcd", "PASSWORD_LOGIN", "203.0.113.10");
        assertThrows(BusinessException.class,
                () -> service.consumeChallenge("challenge", "abcd", "PASSWORD_LOGIN", "203.0.113.10"));
    }

    @Test
    void failsClosedWhenRedisUnavailable() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThrows(BusinessException.class,
                () -> service.issue("PASSWORD_LOGIN", "203.0.113.10"));
    }

    @Test
    void alwaysGeneratesExactlyFourCharactersEvenWhenLegacyLengthIsMisconfigured() throws Exception {
        properties.setFixedCode(null);
        properties.setMinLength(99);
        properties.setMaxLength(99);
        var method = ImageCaptchaService.class.getDeclaredMethod("randomAnswer");
        method.setAccessible(true);

        String answer = (String) method.invoke(service);
        assertTrue(answer.length() == 4);
    }

    @Test
    void rejectsFiveCharacterFixedCode() throws Exception {
        properties.setFixedCode("ABCDE");
        var method = ImageCaptchaService.class.getDeclaredMethod("randomAnswer");
        method.setAccessible(true);

        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service));
        assertTrue(exception.getCause() instanceof BusinessException);
    }
}
