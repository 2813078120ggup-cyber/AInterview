package com.tyut.aiinterview.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetTicketService ticketService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SecurityNotificationService notificationService;
    private final OperationAuditService auditService;

    public PasswordResetService(UserMapper userMapper, CompanyMapper companyMapper,
                                VerificationCodeService verificationCodeService,
                                PasswordResetTicketService ticketService,
                                PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                SecurityNotificationService notificationService,
                                OperationAuditService auditService) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
        this.verificationCodeService = verificationCodeService;
        this.ticketService = ticketService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public AuthDtos.PasswordResetCodeResponse sendCode(AuthDtos.PasswordResetCodeRequest request) {
        NormalizedTarget target = normalize(request.channel(), request.target());
        try {
            UserAccount account = findVerifiedAccount(target);
            boolean deliver = isAccountEnabled(account);
            VerificationCodeService.PasswordResetCodeResult result = verificationCodeService
                    .sendPasswordResetCode(deliver ? account.getId() : null, target.channel(), target.value(), deliver);
            auditService.success("AUTHENTICATION", "PASSWORD_RESET_CODE_REQUESTED", "USER", null, null,
                    "密码重置验证码请求已处理");
            return new AuthDtos.PasswordResetCodeResponse(true, result.cooldownSeconds(), result.expiresInSeconds(),
                    "若该联系方式可用于找回账户，验证码将发送至该联系方式");
        } catch (BusinessException exception) {
            auditService.failure("AUTHENTICATION", "PASSWORD_RESET_CODE_FAILED", "USER", null, null,
                    "密码重置验证码请求处理失败");
            throw exception;
        } catch (RuntimeException exception) {
            auditService.failure("AUTHENTICATION", "PASSWORD_RESET_CODE_FAILED", "USER", null, null,
                    "密码重置验证码请求处理失败");
            throw BusinessException.serviceUnavailable("验证渠道暂不可用，请稍后重试");
        }
    }

    public AuthDtos.PasswordResetVerifyResponse verifyCode(AuthDtos.PasswordResetVerifyRequest request) {
        NormalizedTarget target = normalize(request.channel(), request.target());
        UserAccount user = findVerifiedAccount(target);
        boolean enabled = isAccountEnabled(user);
        try {
            verificationCodeService.verifyPasswordResetCode(enabled ? user.getId() : null,
                    target.channel(), target.value(), request.verificationCode());
        } catch (BusinessException exception) {
            auditService.denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER",
                    user == null ? null : user.getId(), user == null ? null : user.getCompanyId(),
                    "密码重置验证码校验失败");
            throw exception;
        }
        if (!enabled) {
            auditService.denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER", null, null,
                    "密码重置验证码校验失败");
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        int securityVersion = user.getSecurityVersion() == null ? 0 : user.getSecurityVersion();
        PasswordResetTicketService.IssuedTicket ticket = ticketService.issue(user.getId(), securityVersion);
        auditService.success("AUTHENTICATION", "PASSWORD_RESET_VERIFIED", "USER", user.getId(),
                user.getCompanyId(), "密码重置账户验证通过");
        return new AuthDtos.PasswordResetVerifyResponse(ticket.token(), ticket.expiresInSeconds());
    }

    @Transactional
    public AuthDtos.PasswordResetResponse complete(AuthDtos.PasswordResetCompleteRequest request) {
        PasswordResetTicketService.VerifiedTicket ticket = ticketService.consume(request.resetToken());
        UserAccount user = userMapper.selectById(ticket.userId());
        int currentSecurityVersion = user == null || user.getSecurityVersion() == null ? 0 : user.getSecurityVersion();
        if (!isAccountEnabled(user) || currentSecurityVersion != ticket.securityVersion()) {
            auditService.denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER",
                    user == null ? null : user.getId(), user == null ? null : user.getCompanyId(),
                    "密码重置票据对应的账户安全状态已变化");
            throw BusinessException.badRequest("账户验证已失效，请重新验证");
        }
        return changePassword(user, request.newPassword());
    }

    @Transactional
    public AuthDtos.PasswordResetResponse reset(AuthDtos.PasswordResetRequest request) {
        NormalizedTarget target = normalize(request.channel(), request.target());
        UserAccount user = findVerifiedAccount(target);
        boolean enabled = isAccountEnabled(user);
        try {
            verificationCodeService.verifyPasswordResetCode(enabled ? user.getId() : null, target.channel(), target.value(),
                    request.verificationCode());
        } catch (BusinessException exception) {
            auditService.denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER",
                    user == null ? null : user.getId(), user == null ? null : user.getCompanyId(),
                    "密码重置验证码校验失败");
            throw exception;
        }

        if (!enabled) {
            auditService.success("AUTHENTICATION", "PASSWORD_RESET_ACCEPTED", "USER", null, null,
                    "密码重置请求已统一处理");
            return new AuthDtos.PasswordResetResponse("密码重置请求已处理，请使用新密码重新登录");
        }
        return changePassword(user, request.newPassword());
    }

    private AuthDtos.PasswordResetResponse changePassword(UserAccount user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            auditService.denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER", user.getId(),
                    user.getCompanyId(), "密码重置时新旧密码相同");
            throw BusinessException.badRequest("新密码不能与当前密码相同");
        }

        int expectedSecurityVersion = user.getSecurityVersion() == null ? 0 : user.getSecurityVersion();
        int updated = userMapper.updatePasswordAndSecurityVersion(user.getId(),
                passwordEncoder.encode(newPassword), expectedSecurityVersion);
        if (updated != 1) {
            auditService.denied("AUTHENTICATION", "PASSWORD_RESET_REJECTED", "USER", user.getId(),
                    user.getCompanyId(), "密码重置时账户安全版本冲突");
            throw BusinessException.conflict("账户安全状态已变化，请重新获取验证码");
        }
        refreshTokenService.revokeAllSessions(user.getId(), "PASSWORD_RESET");
        UserAccount latest = userMapper.selectById(user.getId());
        auditService.success("AUTHENTICATION", "PASSWORD_RESET_SUCCESS", "USER", user.getId(),
                user.getCompanyId(), "登录密码已重置并撤销全部会话");
        notificationService.notifyPasswordChanged(latest == null ? user : latest);
        return new AuthDtos.PasswordResetResponse("密码已重置，全部设备会话已失效，请重新登录");
    }

    private UserAccount findVerifiedAccount(NormalizedTarget target) {
        LambdaQueryWrapper<UserAccount> query = new LambdaQueryWrapper<>();
        if ("sms".equals(target.channel())) {
            query.eq(UserAccount::getPhone, target.value()).isNotNull(UserAccount::getPhoneVerifiedAt);
        } else {
            query.eq(UserAccount::getEmail, target.value()).isNotNull(UserAccount::getEmailVerifiedAt);
        }
        return userMapper.selectOne(query);
    }

    private boolean isAccountEnabled(UserAccount user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) return false;
        if (user.getCompanyId() == null) return true;
        Company company = companyMapper.selectById(user.getCompanyId());
        return company != null && Integer.valueOf(1).equals(company.getStatus());
    }

    private NormalizedTarget normalize(String channel, String value) {
        String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        if (!"sms".equals(normalizedChannel) && !"email".equals(normalizedChannel)) {
            throw BusinessException.badRequest("密码找回方式不正确");
        }
        String normalizedValue = "sms".equals(normalizedChannel)
                ? VerificationCodeService.normalizePhone(value)
                : VerificationCodeService.normalizeEmail(value).toLowerCase(Locale.ROOT);
        return new NormalizedTarget(normalizedChannel, normalizedValue);
    }

    private record NormalizedTarget(String channel, String value) {}
}
