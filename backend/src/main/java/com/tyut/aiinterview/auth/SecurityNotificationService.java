package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.observability.OperationAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SecurityNotificationService {
    private final SiteNotificationService siteNotificationService;
    private final VerificationCodeService legacyVerificationCodeService;
    private final OperationAuditService legacyAuditService;

    @Autowired
    public SecurityNotificationService(SiteNotificationService siteNotificationService) {
        this.siteNotificationService = siteNotificationService;
        this.legacyVerificationCodeService = null;
        this.legacyAuditService = null;
    }

    SecurityNotificationService(VerificationCodeService verificationCodeService,
                                OperationAuditService auditService) {
        this.siteNotificationService = null;
        this.legacyVerificationCodeService = verificationCodeService;
        this.legacyAuditService = auditService;
    }

    public void notifyPasswordChanged(UserAccount user) {
        if (user == null || user.getId() == null) return;
        String message = "你的 AInterview 登录密码已更新。如非本人操作，请立即联系平台管理员。";
        if (siteNotificationService != null) {
            siteNotificationService.create(user.getId(), "ACCOUNT_SECURITY", "登录密码已更新", message,
                    "USER", user.getId(), "account-security-password-" + user.getSecurityVersion());
            return;
        }
        legacyNotifyOne(user, "sms", user.getPhoneVerifiedAt() == null ? null : user.getPhone(), message);
        legacyNotifyOne(user, "email", user.getEmailVerifiedAt() == null ? null : user.getEmail(), message);
    }

    private void legacyNotifyOne(UserAccount user, String channel, String target, String message) {
        if (!StringUtils.hasText(target) || legacyVerificationCodeService == null) return;
        try {
            legacyVerificationCodeService.sendSecurityNotification(channel, target,
                    "AInterview 账户安全通知", message);
            if (legacyAuditService != null) legacyAuditService.success("ACCOUNT",
                    "PASSWORD_SECURITY_NOTIFICATION_SENT", "USER", user.getId(), user.getCompanyId(),
                    "密码变更安全通知已发送");
        } catch (RuntimeException exception) {
            try {
                if (legacyAuditService != null) legacyAuditService.failure("ACCOUNT",
                        "PASSWORD_SECURITY_NOTIFICATION_FAILED", "USER", user.getId(), user.getCompanyId(),
                        "密码变更安全通知发送失败");
            } catch (RuntimeException ignored) {
                // Compatibility path remains non-blocking for credential changes.
            }
        }
    }
}
