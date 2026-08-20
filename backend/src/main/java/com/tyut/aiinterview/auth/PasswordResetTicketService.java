package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.utils.TokenHashUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** One-time, short-lived proof that a password-reset verification code was accepted. */
@Service
public class PasswordResetTicketService {
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            redis.call('DEL', KEYS[1])
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;

    public PasswordResetTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public IssuedTicket issue(Long userId, int securityVersion) {
        if (userId == null) throw BusinessException.badRequest("验证码错误或已过期");
        String token = TokenHashUtils.generateOpaqueToken();
        try {
            redisTemplate.opsForValue().set(ticketKey(token), userId + "|" + securityVersion,
                    TICKET_TTL.toSeconds(), TimeUnit.SECONDS);
            return new IssuedTicket(token, TICKET_TTL.toSeconds());
        } catch (RuntimeException exception) {
            throw BusinessException.serviceUnavailable("账户验证服务暂不可用，请稍后重试");
        }
    }

    public VerifiedTicket consume(String token) {
        if (!StringUtils.hasText(token)) throw invalidTicket();
        String value;
        try {
            value = redisTemplate.execute(CONSUME_SCRIPT, List.of(ticketKey(token.trim())));
        } catch (RuntimeException exception) {
            throw BusinessException.serviceUnavailable("账户验证服务暂不可用，请稍后重试");
        }
        if (!StringUtils.hasText(value)) throw invalidTicket();
        String[] parts = value.split("\\|", -1);
        if (parts.length != 2) throw invalidTicket();
        try {
            return new VerifiedTicket(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            throw invalidTicket();
        }
    }

    private static BusinessException invalidTicket() {
        return BusinessException.badRequest("账户验证已失效，请重新验证");
    }

    private static String ticketKey(String token) {
        return "auth:password-reset-ticket:" + TokenHashUtils.sha256(token);
    }

    public record IssuedTicket(String token, long expiresInSeconds) {}
    public record VerifiedTicket(Long userId, int securityVersion) {}
}
