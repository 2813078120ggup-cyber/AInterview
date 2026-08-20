package com.tyut.aiinterview.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.utils.TokenHashUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Redis-backed fixed-window rate limiting for public auth endpoints
 * (login, registration and verification-code sending). Runs after the
 * request-id filter so 429 responses carry the same request envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String LOGIN = "/v1/auth/login";
    private static final String REGISTER = "/v1/auth/register";
    private static final String COMPANY_REGISTER = "/v1/auth/company/register";
    private static final String REGISTER_CODE = "/v1/auth/register/code";
    private static final String LOGIN_CODE_SEND = "/v1/auth/login/code/send";
    private static final String LOGIN_CODE = "/v1/auth/login/code";
    private static final String CAPTCHA_CHALLENGE = "/v1/auth/captcha/challenge";
    private static final String PASSWORD_RESET_CODE = "/v1/auth/password/reset/code";
    private static final String PASSWORD_RESET_VERIFY = "/v1/auth/password/reset/verify";
    private static final String PASSWORD_RESET_COMPLETE = "/v1/auth/password/reset/complete";

    private final StringRedisTemplate redisTemplate;
    private final AuthRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(StringRedisTemplate redisTemplate, AuthRateLimitProperties properties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) return true;
        String uri = request.getRequestURI();
        return !uri.endsWith(LOGIN) && !uri.endsWith(REGISTER) && !uri.endsWith(COMPANY_REGISTER) && !uri.endsWith(REGISTER_CODE)
                && !uri.endsWith(LOGIN_CODE_SEND) && !uri.endsWith(LOGIN_CODE)
                && !uri.endsWith(CAPTCHA_CHALLENGE) && !uri.endsWith(PASSWORD_RESET_CODE)
                && !uri.endsWith(PASSWORD_RESET_VERIFY) && !uri.endsWith(PASSWORD_RESET_COMPLETE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        String uri = request.getRequestURI();

        if (uri.endsWith(LOGIN) && !allow("login", clientIp, properties.getLoginPerMinute(), 60)) {
            reject(response);
            return;
        }
        if (uri.endsWith(REGISTER) && !allow("register", clientIp, properties.getRegisterPerMinute(), 60)) {
            reject(response);
            return;
        }
        if (uri.endsWith(COMPANY_REGISTER)
                && !allow("company-register", clientIp, properties.getCompanyRegisterPerMinute(), 60)) {
            reject(response);
            return;
        }
        if ((uri.endsWith(REGISTER_CODE) || uri.endsWith(LOGIN_CODE_SEND) || uri.endsWith(PASSWORD_RESET_CODE))
                && (!allow("code-send", clientIp, properties.getCodeSendPerMinute(), 60)
                    || !allow("code-send-day", clientIp, properties.getCodeSendPerDay(), 86_400))) {
            reject(response);
            return;
        }
        if ((uri.endsWith(LOGIN_CODE) || uri.endsWith(PASSWORD_RESET_VERIFY))
                && !allow("login-code", clientIp, properties.getLoginCodePerMinute(), 60)) {
            reject(response);
            return;
        }
        if (uri.endsWith(PASSWORD_RESET_COMPLETE)
                && !allow("password-reset-complete", clientIp, properties.getRegisterPerMinute(), 60)) {
            reject(response);
            return;
        }
        if (uri.endsWith(CAPTCHA_CHALLENGE)
                && !allow("captcha-challenge", clientIp, properties.getCaptchaChallengePerMinute(), 60)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String bucket, String clientIp, int limit, long windowSeconds) {
        if (limit <= 0) return false;
        try {
            long window = Instant.now().getEpochSecond() / windowSeconds;
            String key = "auth:rl:" + bucket + ":" + TokenHashUtils.sha256(clientIp == null ? "" : clientIp) + ":" + window;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }
            return count != null && count <= limit;
        } catch (Exception exception) {
            // Redis 不可用时放行（fail-open），避免认证接口因限流组件整体不可用
            log.warn("auth rate limit skipped because Redis is unavailable: {}", exception.getMessage());
            return true;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(42900, "请求过于频繁，请稍后再试"));
    }
}
