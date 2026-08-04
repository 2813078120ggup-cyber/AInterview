package com.tyut.aiinterview.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.RefreshToken;
import com.tyut.aiinterview.mapper.RefreshTokenMapper;
import com.tyut.aiinterview.utils.TokenHashUtils;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    public record IssuedToken(Long userId, String plainToken) {}

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper, JwtProperties properties) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.properties = properties;
    }

    @Transactional
    public IssuedToken issue(Long userId, String clientIp, String userAgent) {
        String plainToken = TokenHashUtils.generateOpaqueToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(TokenHashUtils.sha256(plainToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(properties.getRefreshTokenExpireDays()));
        token.setClientIp(clientIp);
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        refreshTokenMapper.insert(token);
        return new IssuedToken(userId, plainToken);
    }

    @Transactional
    public IssuedToken rotate(String plainToken, String clientIp, String userAgent) {
        RefreshToken token = activeToken(plainToken);
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenMapper.updateById(token);
        return issue(token.getUserId(), clientIp, userAgent);
    }

    @Transactional
    public void revoke(String plainToken, Long userId) {
        RefreshToken token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, TokenHashUtils.sha256(plainToken))
                .eq(RefreshToken::getUserId, userId));
        if (token != null && token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenMapper.updateById(token);
        }
    }

    private RefreshToken activeToken(String plainToken) {
        RefreshToken token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, TokenHashUtils.sha256(plainToken)));
        if (token == null || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.forbidden("刷新令牌无效或已过期");
        }
        return token;
    }
}
