package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.utils.TokenHashUtils;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Service
public class VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> CHANGE_PURPOSES = Set.of("CHANGE_PHONE", "CHANGE_EMAIL");
    private static final String PASSWORD_RESET = "PASSWORD_RESET";
    private static final DefaultRedisScript<Long> CONSUME_CODE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            if value ~= ARGV[1] then return -1 end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final JavaMailSender mailSender;
    private final VerificationCodeProperties properties;

    public VerificationCodeService(StringRedisTemplate redisTemplate, RestClient.Builder restClientBuilder,
            ObjectProvider<JavaMailSender> mailSenderProvider, VerificationCodeProperties properties) {
        this.redisTemplate = redisTemplate;
        this.restClient = restClientBuilder.build();
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.properties = properties;
    }

    public void sendRegisterCode(AuthDtos.SendVerificationCodeRequest request) {
        String phone = normalizePhone(request.phone());
        String email = normalizeOptionalEmail(request.email());
        String cooldownKey = cooldownKey(phone);
        Boolean allowed = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1",
                properties.getCooldown().toSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            throw BusinessException.badRequest("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            sendSms(phone, code);
            if (email != null) {
                sendMail(email, code);
            }
            redisTemplate.opsForValue().set(codeKey(phone), code, properties.getTtl().toSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            redisTemplate.delete(cooldownKey);
            throw exception;
        }
    }

    public void verifyRegisterCode(String phone, String code) {
        String normalizedPhone = normalizePhone(phone);
        if (!StringUtils.hasText(code)) {
            throw BusinessException.badRequest("请输入验证码");
        }
        if (!consumeCode(codeKey(normalizedPhone), code)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
    }

    public void sendLoginCode(AuthDtos.SendLoginCodeRequest request) {
        String channel = normalizeChannel(request.channel());
        String target = normalizeLoginTarget(channel, request.target());
        String cooldownKey = loginCooldownKey(channel, target);
        Boolean allowed = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1",
                properties.getCooldown().toSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            throw BusinessException.badRequest("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            if ("sms".equals(channel)) sendSms(target, code); else sendMail(target, code, "登录");
            redisTemplate.opsForValue().set(loginCodeKey(channel, target), code, properties.getTtl().toSeconds(), TimeUnit.SECONDS);
        } catch (BusinessException exception) {
            redisTemplate.delete(cooldownKey);
            throw exception;
        } catch (RestClientException exception) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        } catch (RuntimeException exception) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        }
    }

    public void verifyLoginCode(String channel, String target, String code) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeLoginTarget(normalizedChannel, target);
        if (!StringUtils.hasText(code)) throw BusinessException.badRequest("请输入验证码");
        if (!consumeCode(loginCodeKey(normalizedChannel, normalizedTarget), code)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
    }

    public ChangeCodeResult sendChangeCode(Long userId, String purpose, String target) {
        String normalizedPurpose = normalizeChangePurpose(purpose);
        String normalizedTarget = normalizeChangeTarget(normalizedPurpose, target);
        String prefix = changeKeyPrefix(userId, normalizedPurpose, normalizedTarget);
        String cooldownKey = prefix + ":cooldown";
        Boolean allowed = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1",
                properties.getCooldown().toSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            throw BusinessException.badRequest("验证码发送过于频繁，请稍后再试");
        }
        String dailyKey = "auth:account-code-daily:" + userId + ":" + normalizedPurpose + ":" + java.time.LocalDate.now();
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            redisTemplate.expire(dailyKey, 24L, TimeUnit.HOURS);
        }
        if (dailyCount != null && dailyCount > Math.max(1, properties.getMaxDailyChangeSends())) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.badRequest("验证码发送次数已达上限，请明日再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            if ("CHANGE_PHONE".equals(normalizedPurpose)) sendSms(normalizedTarget, code);
            else sendMail(normalizedTarget, code, "安全变更");
            redisTemplate.opsForValue().set(prefix + ":code", code, properties.getTtl().toSeconds(), TimeUnit.SECONDS);
            redisTemplate.delete(prefix + ":failures");
            return new ChangeCodeResult(properties.getCooldown().toSeconds(), properties.getTtl().toSeconds());
        } catch (BusinessException exception) {
            redisTemplate.delete(cooldownKey);
            throw exception;
        } catch (RestClientException exception) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        } catch (RuntimeException exception) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        }
    }

    public void verifyChangeCode(Long userId, String purpose, String target, String code) {
        String normalizedPurpose = normalizeChangePurpose(purpose);
        String normalizedTarget = normalizeChangeTarget(normalizedPurpose, target);
        if (!StringUtils.hasText(code)) throw BusinessException.badRequest("请输入验证码");
        String prefix = changeKeyPrefix(userId, normalizedPurpose, normalizedTarget);
        String failureKey = prefix + ":failures";
        if (!consumeCode(prefix + ":code", code)) {
            Long failures = redisTemplate.opsForValue().increment(failureKey);
            if (failures != null && failures == 1L) {
                redisTemplate.expire(failureKey, properties.getTtl().toSeconds(), TimeUnit.SECONDS);
            }
            if (failures != null && failures >= Math.max(1, properties.getMaxChangeVerifyFailures())) {
                redisTemplate.delete(prefix + ":code");
                redisTemplate.delete(failureKey);
            }
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        redisTemplate.delete(failureKey);
    }

    public PasswordResetCodeResult sendPasswordResetCode(Long userId, String channel, String target, boolean deliver) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeLoginTarget(normalizedChannel, target);
        requireChannelConfigured(normalizedChannel);
        String prefix = passwordResetKeyPrefix(userId, normalizedChannel, normalizedTarget);
        String cooldownKey = prefix + ":cooldown";
        Boolean allowed = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1",
                properties.getCooldown().toSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            throw BusinessException.badRequest("验证码发送过于频繁，请稍后再试");
        }
        String dailyKey = "auth:password-reset-code-daily:" + PASSWORD_RESET + ":"
                + normalizedChannel + ":" + TokenHashUtils.sha256(normalizedTarget) + ":" + java.time.LocalDate.now();
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) redisTemplate.expire(dailyKey, 24L, TimeUnit.HOURS);
        if (dailyCount != null && dailyCount > Math.max(1, properties.getMaxDailyPasswordResetSends())) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.badRequest("验证码发送次数已达上限，请明日再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            if (deliver) {
                if ("sms".equals(normalizedChannel)) sendSms(normalizedTarget, code);
                else sendMail(normalizedTarget, code, "重置密码");
                redisTemplate.opsForValue().set(prefix + ":code", code,
                        properties.getTtl().toSeconds(), TimeUnit.SECONDS);
                redisTemplate.delete(prefix + ":failures");
            }
            return new PasswordResetCodeResult(properties.getCooldown().toSeconds(), properties.getTtl().toSeconds());
        } catch (BusinessException exception) {
            redisTemplate.delete(cooldownKey);
            throw exception;
        } catch (RestClientException exception) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        } catch (RuntimeException exception) {
            redisTemplate.delete(cooldownKey);
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        }
    }

    public void verifyPasswordResetCode(Long userId, String channel, String target, String code) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeLoginTarget(normalizedChannel, target);
        if (!StringUtils.hasText(code)) throw BusinessException.badRequest("请输入验证码");
        String prefix = passwordResetKeyPrefix(userId, normalizedChannel, normalizedTarget);
        String failureKey = prefix + ":failures";
        if (!consumeCode(prefix + ":code", code)) {
            Long failures = redisTemplate.opsForValue().increment(failureKey);
            if (failures != null && failures == 1L) {
                redisTemplate.expire(failureKey, properties.getTtl().toSeconds(), TimeUnit.SECONDS);
            }
            if (failures != null && failures >= Math.max(1, properties.getMaxPasswordResetVerifyFailures())) {
                redisTemplate.delete(prefix + ":code");
                redisTemplate.delete(failureKey);
            }
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        redisTemplate.delete(failureKey);
    }

    public void sendSecurityNotification(String channel, String target, String message) {
        sendSecurityNotification(channel, target, "AInterview 账户安全通知", message);
    }

    public void sendSecurityNotification(String channel, String target, String subject, String message) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = "sms".equals(normalizedChannel) ? normalizePhone(target) : normalizeEmail(target);
        if ("sms".equals(normalizedChannel)) sendSmsMessage(normalizedTarget, message);
        else sendMailMessage(normalizedTarget, subject, message);
    }

    public boolean isNotificationChannelAvailable(String channel) {
        String normalized = StringUtils.hasText(channel) ? channel.trim().toLowerCase(Locale.ROOT) : "";
        if ("sms".equals(normalized)) {
            return StringUtils.hasText(properties.getSmsHost())
                    && StringUtils.hasText(properties.getSmsPath())
                    && StringUtils.hasText(properties.getSmsAppCode())
                    && StringUtils.hasText(properties.getSmsTemplateId());
        }
        if ("email".equals(normalized)) {
            return mailSender != null && StringUtils.hasText(properties.getMailFrom());
        }
        return false;
    }

    private boolean consumeCode(String key, String submittedCode) {
        Long result = redisTemplate.execute(CONSUME_CODE_SCRIPT, java.util.List.of(key), submittedCode.trim());
        return Long.valueOf(1L).equals(result);
    }

    private void sendSms(String phoneNumber, String code) {
        sendSmsMessage(phoneNumber, "code:" + code);
    }

    private void sendSmsMessage(String phoneNumber, String content) {
        if (!StringUtils.hasText(properties.getSmsAppCode())) {
            throw BusinessException.serviceUnavailable("短信服务暂不可用，请稍后重试");
        }
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("content", content);
        body.add("template_id", properties.getSmsTemplateId());
        body.add("phone_number", phoneNumber);
        String response = restClient.post()
                .uri(properties.getSmsHost() + properties.getSmsPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "APPCODE " + properties.getSmsAppCode())
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("SMS notification sent to {}", mask(phoneNumber));
    }

    private void sendMail(String email, String code) {
        sendMail(email, code, "注册");
    }

    private void sendMail(String email, String code, String scene) {
        String subject = switch (scene) {
            case "登录" -> "AInterview 登录验证码";
            case "重置密码" -> "AInterview 密码重置验证码";
            default -> properties.getMailSubject();
        };
        sendMailMessage(email, subject,
                "您的" + scene + "验证码是：" + code + "，" + properties.getTtl().toMinutes() + " 分钟内有效。");
    }

    private void sendMailMessage(String email, String subject, String content) {
        if (mailSender == null) {
            throw BusinessException.serviceUnavailable("邮箱服务暂不可用，请稍后重试");
        }
        if (!StringUtils.hasText(properties.getMailFrom())) {
            throw BusinessException.serviceUnavailable("邮箱服务暂不可用，请稍后重试");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    private static String normalizeChannel(String channel) {
        String normalized = StringUtils.hasText(channel) ? channel.trim().toLowerCase(Locale.ROOT) : "";
        if (!"sms".equals(normalized) && !"email".equals(normalized)) {
            throw BusinessException.badRequest("验证码发送方式不正确");
        }
        return normalized;
    }

    private static String normalizeLoginTarget(String channel, String target) {
        if ("sms".equals(channel)) return normalizePhone(target);
        String email = normalizeOptionalEmail(target);
        if (email == null) throw BusinessException.badRequest("邮箱格式不正确");
        return email;
    }

    public static String normalizePhone(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!normalized.matches("^1\\d{10}$")) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        return normalized;
    }

    private static String normalizeOptionalEmail(String value) {
        if (!StringUtils.hasText(value)) return null;
        return normalizeEmail(value);
    }

    public static String normalizeEmail(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }
        return normalized;
    }

    private String normalizeChangePurpose(String purpose) {
        String normalized = StringUtils.hasText(purpose) ? purpose.trim().toUpperCase(Locale.ROOT) : "";
        if (!CHANGE_PURPOSES.contains(normalized)) throw BusinessException.badRequest("验证码用途不正确");
        return normalized;
    }

    private String normalizeChangeTarget(String purpose, String target) {
        return "CHANGE_PHONE".equals(purpose) ? normalizePhone(target) : normalizeEmail(target);
    }

    private String changeKeyPrefix(Long userId, String purpose, String target) {
        if (userId == null) throw BusinessException.forbidden("登录已失效");
        return "auth:account-code:" + userId + ":" + purpose + ":" + TokenHashUtils.sha256(target);
    }

    private String passwordResetKeyPrefix(Long userId, String channel, String target) {
        String subject = userId == null ? "ANONYMOUS" : String.valueOf(userId);
        return "auth:password-reset-code:" + subject + ":" + PASSWORD_RESET + ":" + channel + ":"
                + TokenHashUtils.sha256(target);
    }

    private void requireChannelConfigured(String channel) {
        if ("sms".equals(channel) && !StringUtils.hasText(properties.getSmsAppCode())) {
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        }
        if ("email".equals(channel) && (mailSender == null || !StringUtils.hasText(properties.getMailFrom()))) {
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        }
    }

    private static String codeKey(String phone) {
        return "auth:register-code:sms:" + phone;
    }

    private static String cooldownKey(String phone) {
        return "auth:register-code-cooldown:sms:" + phone;
    }

    private static String loginCodeKey(String channel, String target) {
        return "auth:login-code:" + channel + ":" + target;
    }

    private static String loginCooldownKey(String channel, String target) {
        return "auth:login-code-cooldown:" + channel + ":" + target;
    }

    private static String mask(String value) {
        if (value.length() <= 7) return "***";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    public record ChangeCodeResult(long cooldownSeconds, long expiresInSeconds) {}
    public record PasswordResetCodeResult(long cooldownSeconds, long expiresInSeconds) {}
}
