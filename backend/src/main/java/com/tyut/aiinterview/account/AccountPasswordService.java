package com.tyut.aiinterview.account;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.auth.SecurityNotificationService;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPasswordService {
    private final AccountService accountService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final SecurityNotificationService notificationService;
    private final OperationAuditService auditService;

    public AccountPasswordService(AccountService accountService, UserMapper userMapper,
                                  PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                  JwtTokenService jwtTokenService,
                                  SecurityNotificationService notificationService,
                                  OperationAuditService auditService) {
        this.accountService = accountService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public AccountDtos.ChangePasswordResponse change(AccountDtos.ChangePasswordRequest request,
                                                     String clientIp, String userAgent) {
        UserAccount current = accountService.requireCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), current.getPasswordHash())) {
            auditService.denied("ACCOUNT", "PASSWORD_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    "修改密码时当前密码校验失败");
            throw BusinessException.forbidden("当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), current.getPasswordHash())) {
            auditService.denied("ACCOUNT", "PASSWORD_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    "修改密码时新旧密码相同");
            throw BusinessException.badRequest("新密码不能与当前密码相同");
        }

        RefreshTokenService.IssuedToken rotated = refreshTokenService.rotateForUser(
                request.refreshToken(), current.getId(), clientIp, userAgent,
                userId -> current.getId().equals(userId));
        int expectedSecurityVersion = current.getSecurityVersion() == null ? 0 : current.getSecurityVersion();
        int updated = userMapper.updatePasswordAndSecurityVersion(current.getId(),
                passwordEncoder.encode(request.newPassword()), expectedSecurityVersion);
        if (updated != 1) {
            auditService.denied("ACCOUNT", "PASSWORD_CHANGED", "USER", current.getId(), current.getCompanyId(),
                    "修改密码时账户安全版本冲突");
            throw BusinessException.conflict("账户安全状态已变化，请重新登录后再试");
        }

        UserAccount latest = userMapper.selectById(current.getId());
        if (latest == null || !Integer.valueOf(1).equals(latest.getStatus())) {
            throw BusinessException.forbidden("账号已停用");
        }
        refreshTokenService.revokeOtherSessions(latest.getId(), rotated.sessionId(), "PASSWORD_CHANGED");
        auditService.success("ACCOUNT", "PASSWORD_CHANGED", "USER", latest.getId(), latest.getCompanyId(),
                "登录密码已更新并撤销其他设备会话");
        notificationService.notifyPasswordChanged(latest);
        String accessToken = jwtTokenService.createToken(latest.getId(), latest.getUsername(),
                latest.getSecurityVersion(), rotated.sessionId());
        return new AccountDtos.ChangePasswordResponse(accessToken, rotated.plainToken(),
                "当前设备已更新登录凭据，其他设备已退出登录");
    }
}
