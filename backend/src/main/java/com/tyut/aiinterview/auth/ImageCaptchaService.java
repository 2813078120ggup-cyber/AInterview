package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.utils.TokenHashUtils;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Redis-backed, one-time image captcha for public authentication operations. */
@Service
public class ImageCaptchaService {
    public static final Set<String> PURPOSES = Set.of("PASSWORD_LOGIN", "LOGIN_CODE_SEND", "REGISTER_CODE_SEND",
            "PASSWORD_RESET_CODE_SEND");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            redis.call('DEL', KEYS[1])
            local expected = ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3]
            if value == expected then return 1 else return -1 end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ImageCaptchaProperties properties;

    public ImageCaptchaService(StringRedisTemplate redisTemplate, ImageCaptchaProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public ChallengeResult issue(String purpose, String clientIp) {
        String normalizedPurpose = normalizePurpose(purpose);
        String answer = randomAnswer();
        String challengeId = TokenHashUtils.generateOpaqueToken();
        String storedValue = TokenHashUtils.sha256(answer) + "|" + normalizedPurpose + "|" + ipHash(clientIp);
        Duration ttl = positive(properties.getChallengeTtl(), Duration.ofMinutes(2));
        try {
            Boolean stored = redisTemplate.opsForValue().setIfAbsent(challengeKey(challengeId), storedValue,
                    ttl.toSeconds(), TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(stored)) throw new IllegalStateException("image captcha was not stored");
            return new ChallengeResult(challengeId, renderDataUrl(answer), ttl.toSeconds());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw BusinessException.serviceUnavailable("图形验证码服务暂不可用，请稍后重试");
        }
    }

    /** Consume challenge and validate answer, purpose and IP atomically in Redis. */
    public void consumeChallenge(String challengeId, String captchaCode, String purpose, String clientIp) {
        String normalizedPurpose = normalizePurpose(purpose);
        if (!StringUtils.hasText(challengeId) || !StringUtils.hasText(captchaCode)) {
            throw BusinessException.badRequest("请输入图形验证码");
        }
        String normalizedCode = normalizeAnswer(captchaCode);
        Long result;
        try {
            result = redisTemplate.execute(CONSUME_SCRIPT, List.of(challengeKey(challengeId.trim())),
                    TokenHashUtils.sha256(normalizedCode), normalizedPurpose, ipHash(clientIp));
        } catch (RuntimeException exception) {
            throw BusinessException.serviceUnavailable("图形验证码服务暂不可用，请稍后重试");
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw BusinessException.badRequest("图形验证码错误或已过期");
        }
    }

    private String randomAnswer() {
        if (StringUtils.hasText(properties.getFixedCode())) {
            String fixed = normalizeAnswer(properties.getFixedCode());
            if (fixed.length() != 4 || fixed.chars().anyMatch(ch -> ALPHABET.indexOf(ch) < 0)) {
                throw BusinessException.serviceUnavailable("图形验证码配置不正确");
            }
            return fixed;
        }
        // Keep the challenge shape deterministic across login, registration and password reset.
        // The properties remain readable for backwards-compatible configuration binding, but
        // variable-length challenges are intentionally no longer supported.
        StringBuilder answer = new StringBuilder(4);
        for (int index = 0; index < 4; index++) answer.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return answer.toString();
    }

    private String renderDataUrl(String answer) {
        int width = Math.max(160, properties.getWidth());
        int height = Math.max(64, properties.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 246, 241));
            graphics.fillRect(0, 0, width, height);
            drawNoise(graphics, width, height);
            drawAnswer(graphics, answer, width, height);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", output)) throw new IOException("PNG writer unavailable");
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw BusinessException.serviceUnavailable("图形验证码生成失败，请稍后重试");
        } finally {
            graphics.dispose();
        }
    }

    private void drawNoise(Graphics2D graphics, int width, int height) {
        for (int index = 0; index < 8; index++) {
            graphics.setColor(new Color(80 + RANDOM.nextInt(130), 100 + RANDOM.nextInt(120), 130 + RANDOM.nextInt(110), 120));
            graphics.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height), RANDOM.nextInt(width), RANDOM.nextInt(height));
        }
        for (int index = 0; index < 120; index++) {
            graphics.setColor(new Color(80 + RANDOM.nextInt(130), 90 + RANDOM.nextInt(120), 110 + RANDOM.nextInt(120), 140));
            int x = RANDOM.nextInt(width);
            int y = RANDOM.nextInt(height);
            int radius = 1 + RANDOM.nextInt(3);
            graphics.fillOval(x, y, radius, radius);
        }
    }

    private void drawAnswer(Graphics2D graphics, String answer, int width, int height) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(30, height - 28));
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        int totalWidth = metrics.stringWidth(answer);
        int left = Math.max(10, (width - totalWidth) / 2 - 4);
        int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
        int advance = Math.max(20, totalWidth / answer.length());
        for (int index = 0; index < answer.length(); index++) {
            int x = left + index * advance + RANDOM.nextInt(7) - 3;
            int y = baseline + RANDOM.nextInt(9) - 4;
            double angle = Math.toRadians(RANDOM.nextInt(35) - 17);
            AffineTransform original = graphics.getTransform();
            graphics.rotate(angle, x + advance / 2.0, y - metrics.getAscent() / 2.0);
            graphics.setColor(new Color(35 + RANDOM.nextInt(75), 45 + RANDOM.nextInt(75), 70 + RANDOM.nextInt(85)));
            graphics.drawString(String.valueOf(answer.charAt(index)), x, y);
            graphics.setTransform(original);
        }
    }

    private static String normalizePurpose(String purpose) {
        String normalized = StringUtils.hasText(purpose) ? purpose.trim().toUpperCase(Locale.ROOT) : "";
        if (!PURPOSES.contains(normalized)) throw BusinessException.badRequest("图形验证码用途不正确");
        return normalized;
    }

    private static String normalizeAnswer(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
    }

    private static String ipHash(String clientIp) {
        return TokenHashUtils.sha256(clientIp == null ? "" : clientIp);
    }

    private static String challengeKey(String challengeId) {
        return "auth:image-captcha:challenge:" + challengeId;
    }

    public record ChallengeResult(String challengeId, String imageDataUrl, long expiresInSeconds) {}
}
