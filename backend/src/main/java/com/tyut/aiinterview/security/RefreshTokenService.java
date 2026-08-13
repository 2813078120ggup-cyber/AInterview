package com.tyut.aiinterview.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.RefreshToken;
import com.tyut.aiinterview.mapper.RefreshTokenMapper;
import com.tyut.aiinterview.utils.TokenHashUtils;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    public record IssuedToken(Long userId, String plainToken, String sessionId) {}
    public record RevokeOutcome(boolean ownerMatched, boolean changed) {}

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper, JwtProperties properties) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.properties = properties;
    }

    @Transactional
    public IssuedToken issue(Long userId, String clientIp, String userAgent) {
        return issue(userId, UUID.randomUUID().toString(), clientIp, userAgent);
    }

    private IssuedToken issue(Long userId, String sessionId, String clientIp, String userAgent) {
        String plainToken = TokenHashUtils.generateOpaqueToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setSessionId(sessionId == null ? UUID.randomUUID().toString() : sessionId);
        token.setTokenHash(TokenHashUtils.sha256(plainToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(properties.getRefreshTokenExpireDays()));
        token.setClientIp(clientIp);
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        refreshTokenMapper.insert(token);
        return new IssuedToken(userId, plainToken, token.getSessionId());
    }

    @Transactional
    public IssuedToken rotate(String plainToken, String clientIp, String userAgent) {
        return rotate(plainToken, clientIp, userAgent, userId -> true);
    }

    @Transactional
    public IssuedToken rotate(String plainToken, String clientIp, String userAgent, Predicate<Long> accountEnabled) {
        return rotateInternal(plainToken, null, clientIp, userAgent, accountEnabled);
    }

    @Transactional
    public IssuedToken rotateForUser(String plainToken, Long expectedUserId, String clientIp, String userAgent,
                                     Predicate<Long> accountEnabled) {
        return rotateInternal(plainToken, expectedUserId, clientIp, userAgent, accountEnabled);
    }

    private IssuedToken rotateInternal(String plainToken, Long expectedUserId, String clientIp, String userAgent,
                                       Predicate<Long> accountEnabled) {
        RefreshToken token = activeToken(plainToken);
        if (expectedUserId != null && !expectedUserId.equals(token.getUserId())) {
            throw BusinessException.forbidden("刷新令牌无效或已过期");
        }
        if (accountEnabled != null && !accountEnabled.test(token.getUserId())) {
            revokeActive(token, "ACCOUNT_DISABLED");
            throw BusinessException.forbidden("用户不存在或已被禁用");
        }
        LocalDateTime now = LocalDateTime.now();
        token.setLastUsedAt(now);
        token.setRevokedAt(now);
        token.setRevokedReason("ROTATED");
        refreshTokenMapper.updateById(token);
        return issue(token.getUserId(), token.getSessionId(), clientIp, userAgent);
    }

    @Transactional
    public int revokeOtherSessions(Long userId, String currentSessionId, String reason) {
        if (userId == null || currentSessionId == null || currentSessionId.isBlank()) return 0;
        return refreshTokenMapper.revokeOtherSessions(userId, currentSessionId,
                reason == null || reason.isBlank() ? "SECURITY_CHANGE" : reason);
    }

    @Transactional
    public int revokeAllSessions(Long userId, String reason) {
        if (userId == null) return 0;
        return refreshTokenMapper.revokeAllSessions(userId,
                reason == null || reason.isBlank() ? "SECURITY_CHANGE" : reason);
    }

    @Transactional
    public RevokeOutcome revoke(String plainToken, Long userId) {
        return revoke(plainToken, userId, "LOGOUT");
    }

    @Transactional
    public RevokeOutcome revoke(String plainToken, Long userId, String reason) {
        if (plainToken == null || plainToken.isBlank()) return new RevokeOutcome(false, false);
        RefreshToken token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, TokenHashUtils.sha256(plainToken)));
        if (token == null || userId == null || !userId.equals(token.getUserId())) {
            return new RevokeOutcome(false, false);
        }
        if (token.getRevokedAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            token.setLastUsedAt(now);
            token.setRevokedAt(now);
            token.setRevokedReason(reason == null || reason.isBlank() ? "REVOKED" : reason);
            refreshTokenMapper.updateById(token);
            return new RevokeOutcome(true, true);
        }
        return new RevokeOutcome(true, false);
    }

    private RefreshToken activeToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) throw BusinessException.forbidden("刷新令牌无效或已过期");
        RefreshToken token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, TokenHashUtils.sha256(plainToken)));
        if (token == null || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.forbidden("刷新令牌无效或已过期");
        }
        return token;
    }

    private void revokeActive(RefreshToken token, String reason) {
        LocalDateTime now = LocalDateTime.now();
        token.setLastUsedAt(now);
        token.setRevokedAt(now);
        token.setRevokedReason(reason);
        refreshTokenMapper.updateById(token);
    }
}
