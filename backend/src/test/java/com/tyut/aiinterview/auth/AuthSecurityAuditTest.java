package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSecurityAuditTest {
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final CompanyMapper companyMapper = org.mockito.Mockito.mock(CompanyMapper.class);
    private final RoleMapper roleMapper = org.mockito.Mockito.mock(RoleMapper.class);
    private final UserRoleMapper userRoleMapper = org.mockito.Mockito.mock(UserRoleMapper.class);
    private final PermissionMapper permissionMapper = org.mockito.Mockito.mock(PermissionMapper.class);
    private final RolePermissionMapper rolePermissionMapper = org.mockito.Mockito.mock(RolePermissionMapper.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    private final JwtTokenService tokenService = org.mockito.Mockito.mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
    private final VerificationCodeService verificationCodeService = org.mockito.Mockito.mock(VerificationCodeService.class);
    private final OperationAuditService auditService = org.mockito.Mockito.mock(OperationAuditService.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userMapper, companyMapper, roleMapper, userRoleMapper, permissionMapper,
                rolePermissionMapper, passwordEncoder, tokenService, refreshTokenService,
                verificationCodeService, auditService);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.List.of());
    }

    @Test
    void successfulPasswordLoginRecordsLoginAndNewSessionWithoutCredentials() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Password123", "stored-hash")).thenReturn(true);
        when(refreshTokenService.issue(7L, "127.0.0.1", "browser"))
                .thenReturn(new RefreshTokenService.IssuedToken(7L, "plain-refresh", "session-7"));
        when(tokenService.createToken(7L, "candidate", 0, "session-7")).thenReturn("access-token");

        AuthDtos.LoginResponse result = service.login(
                new AuthDtos.LoginRequest("candidate", "Password123"), "127.0.0.1", "browser");

        assertEquals("access-token", result.token());
        verify(auditService).success("AUTHENTICATION", "AUTH_LOGIN_SUCCESS", "USER", 7L, null,
                "密码登录成功");
        verify(auditService).success("AUTHENTICATION", "AUTH_SESSION_CREATED", "USER", 7L, null,
                "创建新的密码登录会话");
    }

    @Test
    void knownPasswordLoginFailureIsLinkedWithoutRecordingIdentifier() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Wrong123", "stored-hash")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.login(
                new AuthDtos.LoginRequest("candidate", "Wrong123"), "127.0.0.1", "browser"));

        verify(auditService).failure("AUTHENTICATION", "AUTH_LOGIN_FAILED", "USER", 7L, null,
                "密码登录失败");
    }

    @Test
    void unknownVerificationTargetIsAuditedWithoutTargetIdentity() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.loginWithCode(
                new AuthDtos.CodeLoginRequest("sms", "13800000000", "123456"), "127.0.0.1", "browser"));

        verify(auditService).failure("AUTHENTICATION", "AUTH_LOGIN_CODE_FAILED", "USER", null, null,
                "验证码登录失败");
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setUsername("candidate");
        user.setPasswordHash("stored-hash");
        user.setSecurityVersion(0);
        user.setStatus(1);
        return user;
    }
}
