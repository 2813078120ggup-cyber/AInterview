package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.RefreshToken;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.RefreshTokenMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountSessionServiceTest {
    private final RefreshTokenMapper mapper = org.mockito.Mockito.mock(RefreshTokenMapper.class);
    private final RefreshTokenService refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
    private final AccountService accountService = org.mockito.Mockito.mock(AccountService.class);
    private final CurrentUser currentUser = org.mockito.Mockito.mock(CurrentUser.class);
    private final OperationAuditService auditService = org.mockito.Mockito.mock(OperationAuditService.class);
    private final AccountSessionService service = new AccountSessionService(mapper, refreshTokenService,
            accountService, currentUser, auditService);
    private final UserAccount user = new UserAccount();

    @BeforeEach
    void setUp() {
        user.setId(7L);
        when(accountService.requireCurrentUser()).thenReturn(user);
        when(currentUser.sessionId()).thenReturn("session-current");
    }

    @Test
    void singleCurrentSessionIsReturnedWithMaskedMetadata() {
        when(mapper.selectList(any())).thenReturn(List.of(token(1L, "session-current", null, 3,
                "192.168.1.44", chromeWindows())));

        List<AccountDtos.AccountSession> result = service.sessions();

        assertEquals(1, result.size());
        assertTrue(result.get(0).current());
        assertEquals("DESKTOP", result.get(0).deviceType());
        assertEquals("Chrome", result.get(0).browser());
        assertEquals("Windows", result.get(0).operatingSystem());
        assertEquals("192.168.1.*", result.get(0).maskedIp());
    }

    @Test
    void rotationTokensAggregateIntoOneSessionAndCurrentSortsFirst() {
        RefreshToken rotated = token(1L, "session-current", LocalDateTime.now().minusMinutes(2), 3,
                "10.0.0.8", chromeWindows());
        rotated.setLastUsedAt(LocalDateTime.now().minusMinutes(2));
        rotated.setRevokedReason("ROTATED");
        RefreshToken current = token(2L, "session-current", null, 3, "10.0.0.8", chromeWindows());
        RefreshToken phone = token(3L, "session-phone", null, 2, "2001:db8:1:2::9",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1");
        when(mapper.selectList(any())).thenReturn(List.of(rotated, phone, current));

        List<AccountDtos.AccountSession> result = service.sessions();

        assertEquals(2, result.size());
        assertEquals("session-current", result.get(0).sessionId());
        assertTrue(result.get(0).current());
        assertEquals("session-phone", result.get(1).sessionId());
        assertFalse(result.get(1).current());
        assertEquals("MOBILE", result.get(1).deviceType());
        assertEquals("2001:db8:1:*", result.get(1).maskedIp());
    }

    @Test
    void revokedAndExpiredSessionsAreOmitted() {
        RefreshToken revoked = token(1L, "session-revoked", LocalDateTime.now().minusMinutes(1), 3,
                null, null);
        RefreshToken expired = token(2L, "session-expired", null, -1, null, null);
        when(mapper.selectList(any())).thenReturn(List.of(revoked, expired));

        assertTrue(service.sessions().isEmpty());
    }

    @Test
    void currentSessionCanBeRevokedAndRepeatedRevocationIsIdempotent() {
        when(mapper.countUserSession(7L, "session-current")).thenReturn(2L);
        when(mapper.revokeUserSession(7L, "session-current", "SESSION_REVOKED")).thenReturn(1, 0);

        service.revoke("session-current");
        service.revoke("session-current");

        verify(mapper, org.mockito.Mockito.times(2))
                .revokeUserSession(7L, "session-current", "SESSION_REVOKED");
        verify(auditService, org.mockito.Mockito.times(2)).success(
                "ACCOUNT", "SESSION_REVOKE", "SESSION", "session-current", null,
                "撤销本人登录设备会话");
    }

    @Test
    void foreignOrMissingSessionUsesSameNotFoundBoundary() {
        when(mapper.countUserSession(7L, "foreign-session")).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.revoke("foreign-session"));

        assertEquals(40400, exception.getCode());
        assertEquals("登录会话不存在", exception.getMessage());
        verify(auditService).denied("ACCOUNT", "SESSION_REVOKE", "SESSION", null, null,
                "撤销登录设备被拒绝：会话不存在或不属于当前用户");
    }

    @Test
    void revokeOthersKeepsCurrentSession() {
        when(refreshTokenService.revokeOtherSessions(7L, "session-current", "OTHER_SESSIONS_REVOKED"))
                .thenReturn(2);

        service.revokeOthers();

        verify(refreshTokenService).revokeOtherSessions(7L, "session-current", "OTHER_SESSIONS_REVOKED");
        verify(auditService).success("ACCOUNT", "OTHER_SESSIONS_REVOKE", "USER", 7L, null,
                "撤销本人其他登录设备会话");
    }

    @Test
    void concurrentRevocationIsIdempotent() throws Exception {
        when(mapper.countUserSession(7L, "session-phone")).thenReturn(1L);
        AtomicInteger updates = new AtomicInteger();
        when(mapper.revokeUserSession(7L, "session-phone", "SESSION_REVOKED"))
                .thenAnswer(invocation -> updates.getAndIncrement() == 0 ? 1 : 0);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                service.revoke("session-phone");
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                service.revoke("session-phone");
                return null;
            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, updates.get());
        verify(mapper, org.mockito.Mockito.times(2))
                .revokeUserSession(7L, "session-phone", "SESSION_REVOKED");
    }

    private RefreshToken token(Long id, String sessionId, LocalDateTime revokedAt, int expiresInDays,
                               String ip, String userAgent) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        token.setUserId(7L);
        token.setSessionId(sessionId);
        token.setCreatedAt(LocalDateTime.now().minusDays(5).plusHours(id));
        token.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        token.setRevokedAt(revokedAt);
        token.setClientIp(ip);
        token.setUserAgent(userAgent);
        return token;
    }

    private String chromeWindows() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";
    }
}
