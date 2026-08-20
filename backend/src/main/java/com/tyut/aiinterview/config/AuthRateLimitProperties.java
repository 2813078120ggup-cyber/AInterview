package com.tyut.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fixed-window rate limits for public authentication endpoints.
 * All limits are keyed by a one-way hash of the client IP seen by the backend.
 */
@Data
@ConfigurationProperties(prefix = "app.ratelimit")
public class AuthRateLimitProperties {
    /** Global switch for authentication rate limiting. */
    private boolean enabled = true;
    /** Max password-login attempts per minute per client IP. */
    private int loginPerMinute = 10;
    /** Max registration attempts per minute per client IP. */
    private int registerPerMinute = 5;
    /** Max enterprise bootstrap registrations per minute per client IP. */
    private int companyRegisterPerMinute = 3;
    /** Max verification-code send requests per minute per client IP. */
    private int codeSendPerMinute = 3;
    /** Max verification-code send requests per day per client IP. */
    private int codeSendPerDay = 20;
    /** Max code-login attempts per minute per client IP. */
    private int loginCodePerMinute = 10;
    /** Max image-captcha challenge requests per minute per client IP. */
    private int captchaChallengePerMinute = 20;
}
