package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceLoginIdentifierTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenService tokenService = mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);

    @Test
    void acceptsEmailAsPasswordLoginIdentifier() {
        UserAccount user = user();
        user.setEmail("candidate@example.com");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.List.of());
        when(passwordEncoder.matches("Password123", "stored-hash")).thenReturn(true);
        when(refreshTokenService.issue(7L, "127.0.0.1", "browser"))
                .thenReturn(new RefreshTokenService.IssuedToken(7L, "refresh", "session"));
        when(tokenService.createToken(7L, "candidate", 0, "session")).thenReturn("access");

        AuthDtos.LoginResponse result = service().login(
                new AuthDtos.LoginRequest("candidate@example.com", "Password123"), "127.0.0.1", "browser");

        assertEquals("access", result.token());
    }

    @Test
    void acceptsPhoneAsPasswordLoginIdentifier() {
        UserAccount user = user();
        user.setPhone("13800138000");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.List.of());
        when(passwordEncoder.matches("Password123", "stored-hash")).thenReturn(true);
        when(refreshTokenService.issue(7L, "127.0.0.1", "browser"))
                .thenReturn(new RefreshTokenService.IssuedToken(7L, "refresh", "session"));
        when(tokenService.createToken(7L, "candidate", 0, "session")).thenReturn("access");

        AuthDtos.LoginResponse result = service().login(
                new AuthDtos.LoginRequest("13800138000", "Password123"), "127.0.0.1", "browser");

        assertEquals("access", result.token());
    }

    @Test
    void keepsLegacyUsernameAsPasswordLoginIdentifier() {
        UserAccount user = user();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.List.of());
        when(passwordEncoder.matches("Password123", "stored-hash")).thenReturn(true);
        when(refreshTokenService.issue(7L, "127.0.0.1", "browser"))
                .thenReturn(new RefreshTokenService.IssuedToken(7L, "refresh", "session"));
        when(tokenService.createToken(7L, "candidate", 0, "session")).thenReturn("access");

        AuthDtos.LoginResponse result = service().login(
                new AuthDtos.LoginRequest("candidate", "Password123"), "127.0.0.1", "browser");

        assertEquals("access", result.token());
    }

    private AuthService service() {
        return new AuthService(userMapper, companyMapper, roleMapper, userRoleMapper, permissionMapper,
                rolePermissionMapper, passwordEncoder, tokenService, refreshTokenService, verificationCodeService);
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setUsername("candidate");
        user.setPasswordHash("stored-hash");
        user.setStatus(1);
        user.setSecurityVersion(0);
        return user;
    }
}
