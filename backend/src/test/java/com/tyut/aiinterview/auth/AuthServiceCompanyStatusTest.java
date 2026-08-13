package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
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

class AuthServiceCompanyStatusTest {
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
    void disabledCompanyMemberCannotLogin() {
        UserAccount user = new UserAccount();
        user.setId(20L);
        user.setUsername("company_member");
        user.setCompanyId(10L);
        user.setStatus(1);
        user.setPasswordHash("encoded");
        Company company = new Company();
        company.setId(10L);
        company.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Password123", "encoded")).thenReturn(true);
        when(companyMapper.selectById(10L)).thenReturn(company);

        AuthService service = new AuthService(userMapper, companyMapper, roleMapper, userRoleMapper,
                permissionMapper, rolePermissionMapper, passwordEncoder, tokenService, refreshTokenService,
                verificationCodeService);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new AuthDtos.LoginRequest("company_member", "Password123"), "127.0.0.1", "test"));

        assertEquals(403, exception.getStatus().value());
    }

    @Test
    void disabledCompanyMemberLoadedForAnOldJwtIsNotEnabled() {
        UserAccount user = new UserAccount();
        user.setId(20L);
        user.setUsername("company_member");
        user.setCompanyId(10L);
        user.setStatus(1);
        user.setSecurityVersion(0);
        Company company = new Company();
        company.setId(10L);
        company.setStatus(0);
        when(userMapper.selectById(20L)).thenReturn(user);
        when(companyMapper.selectById(10L)).thenReturn(company);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.List.of());

        AuthService service = new AuthService(userMapper, companyMapper, roleMapper, userRoleMapper,
                permissionMapper, rolePermissionMapper, passwordEncoder, tokenService, refreshTokenService,
                verificationCodeService);

        assertEquals(false, service.loadUserByUsername("20").isEnabled());
    }
}
