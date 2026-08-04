package com.tyut.aiinterview.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(Long userId, String username) {
        Instant expiresAt = Instant.now().plus(properties.getTokenExpireHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key())
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }

    private SecretKey key() {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 个字符");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
