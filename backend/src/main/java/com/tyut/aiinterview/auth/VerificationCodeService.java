package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.common.BusinessException;
import java.security.SecureRandom;
import java.util.Locale;
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
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
public class VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
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
        String key = codeKey(normalizedPhone);
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cachedCode) || !cachedCode.equals(code.trim())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        redisTemplate.delete(key);
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
        } catch (RuntimeException exception) {
            redisTemplate.delete(cooldownKey);
            throw exception;
        }
    }

    public void verifyLoginCode(String channel, String target, String code) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeLoginTarget(normalizedChannel, target);
        if (!StringUtils.hasText(code)) throw BusinessException.badRequest("请输入验证码");
        String cachedCode = redisTemplate.opsForValue().get(loginCodeKey(normalizedChannel, normalizedTarget));
        if (!StringUtils.hasText(cachedCode) || !cachedCode.equals(code.trim())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        redisTemplate.delete(loginCodeKey(normalizedChannel, normalizedTarget));
    }

    private void sendSms(String phoneNumber, String code) {
        if (!StringUtils.hasText(properties.getSmsAppCode())) {
            throw BusinessException.badRequest("短信服务未配置 AppCode");
        }
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("content", "code:" + code);
        body.add("template_id", properties.getSmsTemplateId());
        body.add("phone_number", phoneNumber);
        String response = restClient.post()
                .uri(properties.getSmsHost() + properties.getSmsPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "APPCODE " + properties.getSmsAppCode())
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("SMS verification code sent to {}, provider response: {}", mask(phoneNumber), response);
    }

    private void sendMail(String email, String code) {
        sendMail(email, code, "注册");
    }

    private void sendMail(String email, String code, String scene) {
        if (mailSender == null) {
            throw BusinessException.badRequest("邮箱服务未启用");
        }
        if (!StringUtils.hasText(properties.getMailFrom())) {
            throw BusinessException.badRequest("邮箱服务未配置发件人");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(email);
        message.setSubject("登录".equals(scene) ? "InterviewOS 登录验证码" : properties.getMailSubject());
        message.setText("您的" + scene + "验证码是：" + code + "，" + properties.getTtl().toMinutes() + " 分钟内有效。");
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

    private static String normalizePhone(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!normalized.matches("^1\\d{10}$")) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        return normalized;
    }

    private static String normalizeOptionalEmail(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }
        return normalized;
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
}
