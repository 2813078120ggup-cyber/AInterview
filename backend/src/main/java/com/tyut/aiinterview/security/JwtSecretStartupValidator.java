package com.tyut.aiinterview.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard for the JWT signing secret. A predictable or missing secret
 * would let anyone forge access tokens, so startup is rejected unless a strong
 * value is provided (or the developer explicitly opted out for local work).
 */
@Component
public class JwtSecretStartupValidator {
    private static final Logger log = LoggerFactory.getLogger(JwtSecretStartupValidator.class);

    public JwtSecretStartupValidator(JwtProperties properties, Environment environment) {
        if (properties.isWeakOrDefault()) {
            if (properties.isRequireStrongJwtSecret()) {
                properties.validateStrong();
            } else if (List.of(environment.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException(
                        "生产环境不允许禁用 JWT 强密钥校验：请设置 JWT_SECRET 并移除 APP_REQUIRE_STRONG_JWT_SECRET=false");
            } else {
                log.warn("JWT_SECRET 未配置或仍为默认/示例占位值；仅限本地开发临时运行，生产环境必须设置随机密钥。");
            }
        }
    }
}
