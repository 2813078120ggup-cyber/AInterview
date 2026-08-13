package com.tyut.aiinterview.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.tyut.aiinterview.domain.RefreshToken;
import com.tyut.aiinterview.mapper.RefreshTokenMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {
    private final RefreshTokenMapper mapper = org.mockito.Mockito.mock(RefreshTokenMapper.class);
    private final JwtProperties properties = new JwtProperties();

    @Test
    void issueCreatesAnIndependentSession() {
        RefreshTokenService service = new RefreshTokenService(mapper, properties);

        RefreshTokenService.IssuedToken issued = service.issue(7L, "127.0.0.1", "test-agent");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(mapper).insert(captor.capture());
        assertEquals(7L, issued.userId());
        assertNotNull(issued.plainToken());
        assertNotNull(captor.getValue().getSessionId());
        assertEquals(36, captor.getValue().getSessionId().length());
    }

    @Test
    void rotationKeepsSessionAndRecordsUseAndReason() {
        RefreshToken active = new RefreshToken();
        active.setId(11L);
        active.setUserId(7L);
        active.setSessionId("session-a");
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(mapper.selectOne(any())).thenReturn(active);

        RefreshTokenService service = new RefreshTokenService(mapper, properties);
        service.rotate("plain-token", "127.0.0.1", "test-agent");

        assertNotNull(active.getLastUsedAt());
        assertNotNull(active.getRevokedAt());
        assertEquals("ROTATED", active.getRevokedReason());
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(mapper).insert(captor.capture());
        assertEquals("session-a", captor.getValue().getSessionId());
    }

    @Test
    void logoutIsIdempotentAndDoesNotRevokeAnotherUserToken() {
        RefreshToken active = new RefreshToken();
        active.setId(12L);
        active.setUserId(7L);
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        active.setRevokedAt(LocalDateTime.now());
        active.setRevokedReason("LOGOUT");
        when(mapper.selectOne(any())).thenReturn(active);

        RefreshTokenService service = new RefreshTokenService(mapper, properties);
        RefreshTokenService.RevokeOutcome repeated = service.revoke("plain-token", 7L);
        RefreshTokenService.RevokeOutcome foreign = service.revoke("plain-token", 8L);

        assertEquals(new RefreshTokenService.RevokeOutcome(true, false), repeated);
        assertEquals(new RefreshTokenService.RevokeOutcome(false, false), foreign);
        verify(mapper, never()).updateById(any(RefreshToken.class));
    }

    @Test
    void disabledAccountRefreshTokenIsRevokedWithoutIssuingANewToken() {
        RefreshToken active = new RefreshToken();
        active.setId(13L);
        active.setUserId(7L);
        active.setSessionId("session-a");
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(mapper.selectOne(any())).thenReturn(active);

        RefreshTokenService service = new RefreshTokenService(mapper, properties);
        org.junit.jupiter.api.Assertions.assertThrows(com.tyut.aiinterview.common.BusinessException.class,
                () -> service.rotate("plain-token", "127.0.0.1", "test-agent", userId -> false));

        assertEquals("ACCOUNT_DISABLED", active.getRevokedReason());
        verify(mapper, never()).insert(any(RefreshToken.class));
    }

    @Test
    void passwordResetRevokesEveryActiveSession() {
        RefreshTokenService service = new RefreshTokenService(mapper, properties);
        when(mapper.revokeAllSessions(7L, "PASSWORD_RESET")).thenReturn(3);

        assertEquals(3, service.revokeAllSessions(7L, "PASSWORD_RESET"));

        verify(mapper).revokeAllSessions(7L, "PASSWORD_RESET");
    }

    @Test
    void revokeOtherSessionsUsesCurrentSessionExclusionAndReason() {
        RefreshTokenService service = new RefreshTokenService(mapper, properties);
        when(mapper.revokeOtherSessions(7L, "session-current", "OTHER_SESSIONS_REVOKED")).thenReturn(2);

        assertEquals(2, service.revokeOtherSessions(7L, "session-current", "OTHER_SESSIONS_REVOKED"));

        verify(mapper).revokeOtherSessions(7L, "session-current", "OTHER_SESSIONS_REVOKED");
    }

    @Test
    void revokedSessionTokenCannotBeRotated() {
        RefreshToken revoked = new RefreshToken();
        revoked.setId(14L);
        revoked.setUserId(7L);
        revoked.setSessionId("session-revoked");
        revoked.setExpiresAt(LocalDateTime.now().plusDays(1));
        revoked.setRevokedAt(LocalDateTime.now());
        revoked.setRevokedReason("SESSION_REVOKED");
        when(mapper.selectOne(any())).thenReturn(revoked);
        RefreshTokenService service = new RefreshTokenService(mapper, properties);

        org.junit.jupiter.api.Assertions.assertThrows(com.tyut.aiinterview.common.BusinessException.class,
                () -> service.rotate("plain-token", "127.0.0.1", "test-agent"));

        verify(mapper, never()).insert(any(RefreshToken.class));
    }
}
