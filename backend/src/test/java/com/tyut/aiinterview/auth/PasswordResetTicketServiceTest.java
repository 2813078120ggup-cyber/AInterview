package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class PasswordResetTicketServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private PasswordResetTicketService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        service = new PasswordResetTicketService(redisTemplate);
    }

    @Test
    void issuedTicketStoresOnlyServerSideProofUnderHashedKey() {
        PasswordResetTicketService.IssuedTicket ticket = service.issue(11L, 4);

        assertFalse(ticket.token().isBlank());
        assertEquals(600, ticket.expiresInSeconds());
        verify(values).set(anyString(), eq("11|4"), eq(600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void consumeReturnsBoundUserAndSecurityVersion() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("11|4");

        PasswordResetTicketService.VerifiedTicket ticket = service.consume("opaque-ticket");

        assertEquals(11L, ticket.userId());
        assertEquals(4, ticket.securityVersion());
    }

    @Test
    void missingTicketIsRejectedAsExpired() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.consume("expired-ticket"));

        assertEquals("账户验证已失效，请重新验证", exception.getMessage());
    }
}
