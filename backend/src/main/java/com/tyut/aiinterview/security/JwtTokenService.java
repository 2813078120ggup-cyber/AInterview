package com.tyut.aiinterview.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(Long userId, String username, Integer securityVersion, String sessionId) {
        Instant expiresAt = Instant.now().plus(properties.getTokenExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("securityVersion", securityVersion == null ? 0 : securityVersion)
                .claim("sessionId", sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key())
                .compact();
    }

    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        Object securityVersionValue = claims.get("securityVersion");
        Integer securityVersion = securityVersionValue instanceof Number number ? number.intValue() : null;
        return new ParsedToken(Long.valueOf(claims.getSubject()), securityVersion, claims.get("sessionId", String.class));
    }

    public record ParsedToken(Long userId, Integer securityVersion, String sessionId) {}

    private SecretKey key() {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 个字符");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
