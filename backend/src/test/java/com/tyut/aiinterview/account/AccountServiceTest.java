package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final MediaFileMapper mediaFileMapper = mock(MediaFileMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final AccountService service = new AccountService(userMapper, userRoleMapper, roleMapper,
            companyMapper, mediaFileMapper, currentUser, auditService);

    @Test
    void candidateReadsOnlyTheirOwnWhitelistedProfile() {
        UserAccount user = user(11L, null, 1, 3);
        user.setUsername("candidate_liu");
        user.setRealName("刘同学");
        user.setEmail("liu@example.com");
        user.setPhone("13800001234");
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setPasswordHash("{bcrypt}hash");
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation(11L, 2L)));
        when(roleMapper.selectBatchIds(eq(List.of(2L)))).thenReturn(List.of(role(2L, "CANDIDATE")));

        AccountDtos.AccountProfile profile = service.profile();

        assertEquals(11L, profile.id());
        assertEquals("CANDIDATE", profile.accountType());
        assertEquals("liu@example.com", profile.email());
        assertEquals("l***@example.com", profile.emailMasked());
        assertEquals("138****1234", profile.phoneMasked());
        assertTrue(profile.emailVerified());
        assertTrue(profile.phoneVerified());
        assertEquals(List.of("PASSWORD", "SMS", "EMAIL"), profile.availableLoginMethods());
        assertEquals(3, profile.version());
        assertFalse(profile.avatarAvailable());
    }

    @Test
    void unverifiedContactsAreNotAvailableForCodeLogin() {
        UserAccount user = user(11L, null, 1, 0);
        user.setEmail("liu@example.com");
        user.setPhone("13800001234");
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), service.profile().availableLoginMethods());
        assertFalse(service.profile().emailVerified());
        assertFalse(service.profile().phoneVerified());
    }

    @Test
    void companyUserAndAdminGetTheirOwnAccountTypeWithoutRolePayload() {
        UserAccount companyUser = user(22L, 88L, 1, 0);
        Company company = company(88L);
        when(currentUser.id()).thenReturn(22L);
        when(userMapper.selectById(22L)).thenReturn(companyUser);
        when(companyMapper.selectById(88L)).thenReturn(company);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation(22L, 3L)));
        when(roleMapper.selectBatchIds(eq(List.of(3L)))).thenReturn(List.of(role(3L, "COMPANY_RECRUITER")));

        assertEquals("COMPANY", service.profile().accountType());

        UserAccount admin = user(33L, null, 1, 1);
        when(currentUser.id()).thenReturn(33L);
        when(userMapper.selectById(33L)).thenReturn(admin);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation(33L, 1L)));
        when(roleMapper.selectBatchIds(eq(List.of(1L)))).thenReturn(List.of(role(1L, "ADMIN")));

        assertEquals("ADMIN", service.profile().accountType());
    }

    @Test
    void requestCannotTargetAnotherUserAndProtectedFieldsAreNotUsed() {
        UserAccount user = user(11L, null, 1, 4);
        user.setRealName("原姓名");
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user);

        AccountDtos.UpdateProfileRequest request = new AccountDtos.UpdateProfileRequest(" 新姓名 ", 4);
        when(userMapper.updateProfileWithVersion(11L, "新姓名", 4)).thenReturn(1);
        when(userMapper.selectById(11L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        service.updateProfile(request);

        verify(userMapper).updateProfileWithVersion(11L, "新姓名", 4);
        verify(userMapper, never()).updateProfileWithVersion(eq(99L), any(), any());
        verify(auditService).success("ACCOUNT", "PROFILE_UPDATED", "USER", 11L, null, "更新本人账户资料");
    }

    @Test
    void trimsAndValidatesRealName() {
        UserAccount user = user(11L, null, 1, 0);
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user);

        assertThrows(BusinessException.class,
                () -> service.updateProfile(new AccountDtos.UpdateProfileRequest("   ", 0)));
        verify(userMapper, never()).updateProfileWithVersion(any(), any(), any());
    }

    @Test
    void rejectsRealNameLongerThan64Characters() {
        UserAccount user = user(11L, null, 1, 0);
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user);

        String longName = "a".repeat(65);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(new AccountDtos.UpdateProfileRequest(longName, 0)));

        assertEquals(400, exception.getStatus().value());
        verify(userMapper, never()).updateProfileWithVersion(any(), any(), any());
    }

    @Test
    void staleVersionReturnsConflictAndDoesNotOverwrite() {
        UserAccount user = user(11L, null, 1, 2);
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(new AccountDtos.UpdateProfileRequest("新姓名", 1)));

        assertEquals(409, exception.getStatus().value());
        verify(userMapper, never()).updateProfileWithVersion(any(), any(), any());
        verify(auditService).denied("ACCOUNT", "PROFILE_UPDATED", "USER", 11L, null, "本人账户资料版本冲突");
    }

    @Test
    void conditionalUpdateRaceReturnsConflictInsteadOfOverwriting() {
        UserAccount user = user(11L, null, 1, 2);
        UserAccount latest = user(11L, null, 1, 3);
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(user, latest);
        when(userMapper.updateProfileWithVersion(11L, "新姓名", 2)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(new AccountDtos.UpdateProfileRequest("新姓名", 2)));

        assertEquals(409, exception.getStatus().value());
        verify(userMapper).updateProfileWithVersion(11L, "新姓名", 2);
        verify(auditService).denied("ACCOUNT", "PROFILE_UPDATED", "USER", 11L, null, "本人账户资料版本冲突");
    }

    @Test
    void disabledUserOrCompanyCannotReadOrUpdateProfile() {
        UserAccount disabled = user(11L, null, 0, 0);
        when(currentUser.id()).thenReturn(11L);
        when(userMapper.selectById(11L)).thenReturn(disabled);
        assertEquals(403, assertThrows(BusinessException.class, service::profile).getStatus().value());

        UserAccount companyUser = user(12L, 88L, 1, 0);
        Company company = company(88L);
        company.setStatus(0);
        when(currentUser.id()).thenReturn(12L);
        when(userMapper.selectById(12L)).thenReturn(companyUser);
        when(companyMapper.selectById(88L)).thenReturn(company);
        assertEquals(403, assertThrows(BusinessException.class, service::profile).getStatus().value());
    }

    private UserAccount user(Long id, Long companyId, int status, int version) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setStatus(status);
        user.setVersion(version);
        user.setRealName("测试用户");
        return user;
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setStatus(1);
        return company;
    }

    private UserRole relation(Long userId, Long roleId) {
        UserRole relation = new UserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }
}
