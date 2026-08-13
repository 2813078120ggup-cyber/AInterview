package com.tyut.aiinterview.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.auth.VerificationCodeService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountContactService {
    private static final String PHONE = "PHONE";
    private static final String EMAIL = "EMAIL";

    private final AccountService accountService;
    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final OperationAuditService auditService;

    public AccountContactService(AccountService accountService, UserMapper userMapper,
                                 VerificationCodeService verificationCodeService,
                                 PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                 JwtTokenService jwtTokenService, OperationAuditService auditService) {
        this.accountService = accountService;
        this.userMapper = userMapper;
        this.verificationCodeService = verificationCodeService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
    }

    public AccountDtos.ChangeCodeResponse sendCode(String channel, AccountDtos.ChangeCodeRequest request) {
        UserAccount current = accountService.requireCurrentUser();
        String normalizedChannel = normalizeChannel(channel);
        String target = normalizeTarget(normalizedChannel, request == null ? null : request.target());
        try {
            VerificationCodeService.ChangeCodeResult result = verificationCodeService.sendChangeCode(
                    current.getId(), purpose(normalizedChannel), target);
            auditService.success("ACCOUNT", "CONTACT_CODE_SENT", "USER", current.getId(), current.getCompanyId(),
                    "发送" + channelLabel(normalizedChannel) + "变更验证码");
            return new AccountDtos.ChangeCodeResponse(result.cooldownSeconds(), result.expiresInSeconds());
        } catch (RuntimeException exception) {
            auditService.failure("ACCOUNT", "CONTACT_CODE_SEND_FAILED", "USER", current.getId(), current.getCompanyId(),
                    "发送" + channelLabel(normalizedChannel) + "变更验证码失败");
            throw exception;
        }
    }

    @Transactional
    public AccountDtos.ContactChangeResponse change(String channel, AccountDtos.ChangeContactRequest request,
                                                    String clientIp, String userAgent) {
        UserAccount current = accountService.requireCurrentUser();
        String normalizedChannel = normalizeChannel(channel);
        String target = normalizeTarget(normalizedChannel, request.target());
        if (!passwordEncoder.matches(request.currentPassword(), current.getPasswordHash())) {
            auditService.denied("ACCOUNT", "CONTACT_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    channelLabel(normalizedChannel) + "变更当前密码校验失败");
            throw BusinessException.forbidden("当前密码不正确");
        }
        try {
            verificationCodeService.verifyChangeCode(current.getId(), purpose(normalizedChannel), target,
                    request.verificationCode());
        } catch (RuntimeException exception) {
            auditService.denied("ACCOUNT", "CONTACT_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    channelLabel(normalizedChannel) + "变更验证码校验失败");
            throw exception;
        }
        ensureAvailable(normalizedChannel, target, current.getId());

        RefreshTokenService.IssuedToken rotated = refreshTokenService.rotateForUser(
                request.refreshToken(), current.getId(), clientIp, userAgent,
                userId -> userId != null && userId.equals(current.getId()));
        int expectedVersion = current.getVersion() == null ? 0 : current.getVersion();
        int updated;
        try {
            updated = PHONE.equals(normalizedChannel)
                    ? userMapper.updatePhoneWithVerification(current.getId(), target, expectedVersion)
                    : userMapper.updateEmailWithVerification(current.getId(), target, expectedVersion);
        } catch (DuplicateKeyException exception) {
            auditService.denied("ACCOUNT", "CONTACT_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    channelLabel(normalizedChannel) + "变更目标不可用");
            throw BusinessException.conflict("该联系方式不可用");
        }
        if (updated != 1) {
            auditService.denied("ACCOUNT", "CONTACT_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    channelLabel(normalizedChannel) + "变更版本冲突");
            throw BusinessException.conflict("账户资料已被其他请求更新，请刷新后重试");
        }
        UserAccount latest = userMapper.selectById(current.getId());
        if (latest == null || !Objects.equals(latest.getStatus(), 1)) throw BusinessException.forbidden("账号已停用");
        refreshTokenService.revokeOtherSessions(current.getId(), rotated.sessionId(), "CONTACT_CHANGED");
        auditService.success("ACCOUNT", "CONTACT_CHANGED", "USER", current.getId(), latest.getCompanyId(),
                "变更" + channelLabel(normalizedChannel) + "并撤销其他设备会话");
        notifyChange(normalizedChannel, current, target);
        String accessToken = jwtTokenService.createToken(latest.getId(), latest.getUsername(),
                latest.getSecurityVersion(), rotated.sessionId());
        return new AccountDtos.ContactChangeResponse(accountService.profile(), accessToken, rotated.plainToken());
    }

    private void ensureAvailable(String channel, String target, Long currentUserId) {
        LambdaQueryWrapper<UserAccount> query = new LambdaQueryWrapper<>();
        if (PHONE.equals(channel)) query.eq(UserAccount::getPhone, target);
        else query.eq(UserAccount::getEmail, target);
        UserAccount existing = userMapper.selectOne(query);
        if (existing != null && !Objects.equals(existing.getId(), currentUserId)) throw BusinessException.conflict("该联系方式不可用");
    }

    private void notifyChange(String channel, UserAccount current, String newTarget) {
        String oldTarget = PHONE.equals(channel) ? current.getPhone() : current.getEmail();
        String message = "你的账户联系方式已变更。如非本人操作，请立即重新登录并联系平台管理员。";
        notifyOne(channel, oldTarget, message, current);
        if (!Objects.equals(oldTarget, newTarget)) notifyOne(channel, newTarget, message, current);
    }

    private void notifyOne(String channel, String target, String message, UserAccount current) {
        if (target == null || target.isBlank()) return;
        try {
            verificationCodeService.sendSecurityNotification(PHONE.equals(channel) ? "sms" : "email", target, message);
            auditService.success("ACCOUNT", "CONTACT_CHANGE_NOTIFICATION_SENT", "USER", current.getId(), current.getCompanyId(), "联系方式变更安全通知已发送");
        } catch (RuntimeException exception) {
            try {
                auditService.failure("ACCOUNT", "CONTACT_CHANGE_NOTIFICATION_FAILED", "USER", current.getId(), current.getCompanyId(), "联系方式变更安全通知发送失败");
            } catch (RuntimeException auditFailure) {
                // Notification delivery must not turn an already committed contact change into a rollback.
            }
        }
    }

    private String normalizeChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!PHONE.equals(normalized) && !EMAIL.equals(normalized)) throw BusinessException.badRequest("联系方式类型不正确");
        return normalized;
    }

    private String normalizeTarget(String channel, String target) {
        return PHONE.equals(channel) ? VerificationCodeService.normalizePhone(target)
                : VerificationCodeService.normalizeEmail(target).toLowerCase(Locale.ROOT);
    }

    private String purpose(String channel) { return PHONE.equals(channel) ? "CHANGE_PHONE" : "CHANGE_EMAIL"; }
    private String channelLabel(String channel) { return PHONE.equals(channel) ? "手机号" : "邮箱"; }
}
