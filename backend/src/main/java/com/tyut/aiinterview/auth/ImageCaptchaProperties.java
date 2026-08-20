package com.tyut.aiinterview.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.image-captcha")
public class ImageCaptchaProperties {
    private Duration challengeTtl = Duration.ofMinutes(2);
    private int width = 240;
    private int height = 88;
    /** Kept as bound properties for compatibility; the service always emits exactly four characters. */
    private int minLength = 4;
    private int maxLength = 4;
    /** Test-only override. Leave empty in every non-test environment. */
    private String fixedCode;

    public Duration getChallengeTtl() { return challengeTtl; }
    public void setChallengeTtl(Duration challengeTtl) { this.challengeTtl = challengeTtl; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public int getMinLength() { return minLength; }
    public void setMinLength(int minLength) { this.minLength = minLength; }
    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

    public String getFixedCode() { return fixedCode; }

    public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }
}
