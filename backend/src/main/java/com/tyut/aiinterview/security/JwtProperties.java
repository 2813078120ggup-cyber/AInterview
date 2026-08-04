package com.tyut.aiinterview.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.security")
public class JwtProperties {
    /** Fallback shipped in application.yml; never acceptable for startup. */
    public static final String DEFAULT_JWT_SECRET = "change-this-secret-to-a-base64-encoded-32-byte-value";
    /** Public placeholder documented in .env.example; predictable and must not be used. */
    public static final String EXAMPLE_JWT_SECRET = "replace-with-a-random-jwt-secret-at-least-32-characters";

    private String jwtSecret;
    private long tokenExpireHours = 24;
    private long refreshTokenExpireDays = 14;
    private boolean requireStrongJwtSecret = true;

    public boolean isWeakOrDefault() {
        return jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32
                || DEFAULT_JWT_SECRET.equals(jwtSecret) || EXAMPLE_JWT_SECRET.equals(jwtSecret);
    }

    public void validateStrong() {
        if (requireStrongJwtSecret && isWeakOrDefault()) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置、仍为默认/示例占位值或长度不足 32 字符，禁止启动。"
                    + "请生成随机密钥并设置 JWT_SECRET 环境变量；"
                    + "仅本地开发可显式设置 APP_REQUIRE_STRONG_JWT_SECRET=false 临时放行。");
        }
    }
}
