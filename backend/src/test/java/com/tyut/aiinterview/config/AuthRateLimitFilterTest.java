package com.tyut.aiinterview.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {
    private final Map<String, AtomicLong> counters = new HashMap<>();
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> ops;
    private AuthRateLimitProperties properties;
    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        });
        properties = new AuthRateLimitProperties();
        filter = new AuthRateLimitFilter(redisTemplate, properties,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private MockHttpServletResponse invoke(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksPasswordLoginAfterTenAttemptsPerMinute() throws Exception {
        for (int index = 0; index < 10; index++) {
            MockHttpServletResponse response = invoke("/api/v1/auth/login");
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse blocked = invoke("/api/v1/auth/login");
        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("42900"));
        assertEquals("60", blocked.getHeader("Retry-After"));
    }

    @Test
    void blocksVerificationCodeSendsAfterDailyLimit() throws Exception {
        properties.setCodeSendPerMinute(100);
        properties.setCodeSendPerDay(3);
        for (int index = 0; index < 3; index++) {
            MockHttpServletResponse response = invoke("/api/v1/auth/register/code");
            assertEquals(200, response.getStatus());
        }
        assertEquals(429, invoke("/api/v1/auth/register/code").getStatus());
        assertEquals(429, invoke("/api/v1/auth/login/code/send").getStatus());
        assertEquals(429, invoke("/api/v1/auth/password/reset/code").getStatus());
    }

    @Test
    void appliesDedicatedImageChallengeLimit() throws Exception {
        properties.setCaptchaChallengePerMinute(2);
        assertEquals(200, invoke("/api/v1/auth/captcha/challenge").getStatus());
        assertEquals(200, invoke("/api/v1/auth/captcha/challenge").getStatus());
        assertEquals(429, invoke("/api/v1/auth/captcha/challenge").getStatus());
    }

    @Test
    void appliesDedicatedCompanyRegistrationLimit() throws Exception {
        properties.setCompanyRegisterPerMinute(2);
        assertEquals(200, invoke("/api/v1/auth/company/register").getStatus());
        assertEquals(200, invoke("/api/v1/auth/company/register").getStatus());
        assertEquals(429, invoke("/api/v1/auth/company/register").getStatus());
    }

    @Test
    void skipsAllLimitsWhenDisabled() throws Exception {
        properties.setEnabled(false);
        for (int index = 0; index < 30; index++) {
            assertEquals(200, invoke("/api/v1/auth/login").getStatus());
        }
    }

    @Test
    void ignoresNonAuthEndpoints() throws Exception {
        for (int index = 0; index < 30; index++) {
            assertEquals(200, invoke("/api/v1/interviews").getStatus());
        }
    }

    @Test
    void allowsRequestsWhenRedisIsUnavailable() throws Exception {
        when(ops.increment(any())).thenThrow(new RedisConnectionFailureException("redis down"));
        for (int index = 0; index < 30; index++) {
            assertEquals(200, invoke("/api/v1/auth/login").getStatus());
        }
    }
}
