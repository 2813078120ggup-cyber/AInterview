package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class CompanyRegistrationServiceTest {
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
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final Role adminRole = new Role();

    @BeforeEach
    void setUp() {
        adminRole.setId(9L);
        adminRole.setRoleCode("COMPANY_ADMIN");
        adminRole.setStatus(1);
        when(userMapper.exists(any())).thenReturn(false);
        when(companyMapper.exists(any())).thenReturn(false);
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded-password");
        doAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(100L);
            return 1;
        }).when(companyMapper).insert(any(Company.class));
        doAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(200L);
            return 1;
        }).when(userMapper).insert(any(UserAccount.class));
    }

    @Test
    void createsIndependentTenantVerifiedAdminAndAuditRecord() {
        AuthService service = service();

        AuthDtos.CompanyRegisterResponse response = service.registerCompany(request());

        assertEquals(100L, response.companyId());
        assertEquals("COMPANY_ADMIN", response.admin().roles().get(0));
        assertEquals(100L, response.admin().companyId());
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyMapper).insert(companyCaptor.capture());
        assertEquals("星云科技", companyCaptor.getValue().getName());
        assertEquals("13800138000", companyCaptor.getValue().getRecruitmentContactPhone());
        assertEquals(200L, companyCaptor.getValue().getCreatedBy());
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals(100L, userCaptor.getValue().getCompanyId());
        assertEquals(LocalDateTime.class, userCaptor.getValue().getPhoneVerifiedAt().getClass());
        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertEquals(200L, roleCaptor.getValue().getUserId());
        assertEquals(9L, roleCaptor.getValue().getRoleId());
        verify(verificationCodeService).verifyRegisterCode("13800138000", "123456");
        verify(auditService).success("AUTHENTICATION", "COMPANY_REGISTER_SUCCESS", "COMPANY", 100L, 100L,
                "公开企业注册并创建首个企业管理员");
    }

    @Test
    void rejectsDuplicatePhoneBeforeConsumingVerificationCode() {
        when(userMapper.exists(any())).thenReturn(true);
        AuthService service = service();

        assertThrows(BusinessException.class, () -> service.registerCompany(request()));

        verify(verificationCodeService, never()).verifyRegisterCode(any(), any());
        verify(companyMapper, never()).insert(any(Company.class));
        verify(auditService).failure("AUTHENTICATION", "COMPANY_REGISTER_FAILED", "COMPANY", null, null,
                "公开企业注册失败");
    }

    @Test
    void validatesWebsiteAndDescriptionOnTheServer() {
        AuthService service = service();
        AuthDtos.CompanyRegisterRequest invalid = new AuthDtos.CompanyRegisterRequest(
                "hr_admin", "Password123", "林晓雯", "hr@example.com", "13800138000", "123456",
                "星云科技", "星云", "人工智能", "500-999人", "北京", "javascript:alert(1)",
                "正常简介", "李明", null);

        assertThrows(BusinessException.class, () -> service.registerCompany(invalid));
        verify(verificationCodeService, never()).verifyRegisterCode(any(), any());
    }

    @Test
    void validatesCredentialsAndVerificationCodeEvenWhenCalledBelowTheController() {
        AuthService service = service();
        AuthDtos.CompanyRegisterRequest invalid = new AuthDtos.CompanyRegisterRequest(
                "中文账号", "weak", "林晓雯", "hr@example.com", "13800138000", "bad",
                "星云科技", "星云", "人工智能", "500-999人", "北京", null, null, null, null);

        assertThrows(BusinessException.class, () -> service.registerCompany(invalid));
        verify(verificationCodeService, never()).verifyRegisterCode(any(), any());
    }

    private AuthService service() {
        return new AuthService(userMapper, companyMapper, roleMapper, userRoleMapper, permissionMapper,
                rolePermissionMapper, passwordEncoder, tokenService, refreshTokenService,
                verificationCodeService, auditService);
    }

    private AuthDtos.CompanyRegisterRequest request() {
        return new AuthDtos.CompanyRegisterRequest(
                "hr_admin", "Password123", "林晓雯", "hr@example.com", "13800138000", "123456",
                "星云科技", "星云", "人工智能", "500-999人", "北京", "https://example.com",
                "专注企业智能化与云原生平台建设。", "李明", null);
    }
}
