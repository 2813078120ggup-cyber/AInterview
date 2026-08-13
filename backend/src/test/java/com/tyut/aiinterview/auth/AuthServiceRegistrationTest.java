package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.Role;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceRegistrationTest {
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
    void verifiedRegistrationRecordsOnlyPhoneVerificationTime() {
        Role candidate = new Role();
        candidate.setId(3L);
        candidate.setRoleCode("CANDIDATE");
        when(userMapper.exists(any())).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");
        when(roleMapper.selectOne(any())).thenReturn(candidate);

        AuthService service = new AuthService(userMapper, companyMapper, roleMapper, userRoleMapper,
                permissionMapper, rolePermissionMapper, passwordEncoder, tokenService, refreshTokenService,
                verificationCodeService);

        service.register(new AuthDtos.RegisterRequest("candidate_new", "Password123", "新候选人",
                "candidate@example.com", "13800138000", "123456"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getPhoneVerifiedAt());
        assertNull(captor.getValue().getEmailVerifiedAt());
    }
}
