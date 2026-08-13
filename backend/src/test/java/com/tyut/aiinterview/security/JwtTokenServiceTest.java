package com.tyut.aiinterview.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    @Test
    void createsShortLivedJwtWithSecurityAndSessionClaims() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret("a-strong-random-secret-with-at-least-32-characters");
        JwtTokenService service = new JwtTokenService(properties);

        String token = service.createToken(7L, "candidate", 3, "session-a");
        JwtTokenService.ParsedToken parsed = service.parse(token);

        assertEquals(7L, parsed.userId());
        assertEquals(3, parsed.securityVersion());
        assertEquals("session-a", parsed.sessionId());
        assertNotNull(token);
    }

    @Test
    void defaultsAccessTokenLifetimeToTwentyMinutes() {
        JwtProperties properties = new JwtProperties();
        assertEquals(20, properties.getTokenExpireMinutes());
    }
}
