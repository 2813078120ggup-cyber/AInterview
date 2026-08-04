package com.tyut.aiinterview.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.verification-code")
public class VerificationCodeProperties {
    private Duration ttl = Duration.ofMinutes(5);
    private Duration cooldown = Duration.ofSeconds(60);
    private String smsHost = "https://dfsns.market.alicloudapi.com";
    private String smsPath = "/data/send_sms";
    private String smsAppCode;
    private String smsTemplateId = "CST_ptdie100";
    private String mailFrom;
    private String mailSubject = "注册验证码";

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getCooldown() { return cooldown; }
    public void setCooldown(Duration cooldown) { this.cooldown = cooldown; }
    public String getSmsHost() { return smsHost; }
    public void setSmsHost(String smsHost) { this.smsHost = smsHost; }
    public String getSmsPath() { return smsPath; }
    public void setSmsPath(String smsPath) { this.smsPath = smsPath; }
    public String getSmsAppCode() { return smsAppCode; }
    public void setSmsAppCode(String smsAppCode) { this.smsAppCode = smsAppCode; }
    public String getSmsTemplateId() { return smsTemplateId; }
    public void setSmsTemplateId(String smsTemplateId) { this.smsTemplateId = smsTemplateId; }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String mailFrom) { this.mailFrom = mailFrom; }
    public String getMailSubject() { return mailSubject; }
    public void setMailSubject(String mailSubject) { this.mailSubject = mailSubject; }
}
