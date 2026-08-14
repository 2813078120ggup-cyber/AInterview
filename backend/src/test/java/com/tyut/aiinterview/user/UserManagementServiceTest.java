package com.tyut.aiinterview.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.AdminUserMapper;
import com.tyut.aiinterview.mapper.AdminUserRow;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserManagementServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final AdminUserMapper adminUserMapper = mock(AdminUserMapper.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final UserManagementService service = new UserManagementService(userMapper, userRoleMapper, roleMapper,
            passwordEncoder, currentUser, companyMapper, adminUserMapper, auditService, refreshTokenService);

    @Test
    void appliesRoleCompanyStatusAndCreatedDateFiltersInDatabaseQuery() {
        AdminUserRow row = new AdminUserRow();
        row.setId(9L);
        row.setUsername("recruiter");
        row.setRealName("Recruiter");
        row.setStatus(1);
        when(adminUserMapper.selectPage(eq("recruit"), eq("COMPANY_RECRUITER"), eq(88L), eq(1),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 9, 1, 0, 0)), eq(20L), eq(20L), eq(false)))
                .thenReturn(List.of(row));
        when(adminUserMapper.count(eq("recruit"), eq("COMPANY_RECRUITER"), eq(88L), eq(1),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 9, 1, 0, 0)), eq(false)))
                .thenReturn(1L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        var result = service.page(new UserDtos.UserQuery(2L, 20L, "recruit", 1,
                "company_recruiter", 88L, "2026-08-01", "2026-08-31"));

        assertEquals(1L, result.total());
        assertEquals("recruiter", result.records().get(0).username());
        verify(adminUserMapper).selectPage(eq("recruit"), eq("COMPANY_RECRUITER"), eq(88L), eq(1),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 9, 1, 0, 0)), eq(20L), eq(20L), eq(false));
    }

    @Test
    void employeePageUsesTheServerSidePlatformEmployeeScope() {
        when(adminUserMapper.selectPage(eq("admin"), eq("ADMIN"), eq(null), eq(1),
                eq(null), eq(null), eq(0L), eq(20L), eq(true))).thenReturn(List.of());
        when(adminUserMapper.count(eq("admin"), eq("ADMIN"), eq(null), eq(1),
                eq(null), eq(null), eq(true))).thenReturn(0L);

        service.employeePage(new UserDtos.UserQuery(1L, 20L, "admin", 1,
                "admin", null, null, null));

        verify(adminUserMapper).selectPage(eq("admin"), eq("ADMIN"), eq(null), eq(1),
                eq(null), eq(null), eq(0L), eq(20L), eq(true));
    }

    @Test
    void employeeDetailRejectsAccountsOutsideThePlatformEmployeeScope() {
        when(adminUserMapper.countPlatformEmployeeById(10L)).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.employeeDetail(10L));

        assertEquals(404, exception.getStatus().value());
        assertEquals("平台员工不存在", exception.getMessage());
        verify(adminUserMapper, never()).selectById(10L);
    }

    @Test
    void cannotDisableTheLastSuperAdmin() {
        UserAccount admin = user(7L, null, 1);
        when(currentUser.id()).thenReturn(99L);
        when(userMapper.selectById(7L)).thenReturn(admin);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L, 1L)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(1L, "ADMIN")));
        when(adminUserMapper.countActiveUsersByRoleCode("ADMIN")).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(7L, new UserDtos.UpdateStatusRequest(0)));

        assertEquals(409, exception.getStatus().value());
        verify(userMapper, never()).updateById(any(UserAccount.class));
        verify(auditService).denied("USER_MANAGEMENT", "USER_STATUS_UPDATED", "USER", 7L, null, "禁止停用最后一个超级管理员");
    }

    @Test
    void cannotRemoveTheLastSuperAdminRole() {
        UserAccount admin = user(7L, null, 1);
        Role candidate = role(2L, "CANDIDATE");
        when(userMapper.selectById(7L)).thenReturn(admin);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L, 1L)));
        when(roleMapper.selectBatchIds(eq(List.of(2L)))).thenReturn(List.of(candidate));
        when(roleMapper.selectBatchIds(eq(List.of(1L)))).thenReturn(List.of(role(1L, "ADMIN")));
        when(adminUserMapper.countActiveUsersByRoleCode("ADMIN")).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(7L, new UserDtos.AssignRolesRequest(List.of(2L))));

        assertEquals(409, exception.getStatus().value());
        verify(userRoleMapper, never()).delete(any());
        verify(auditService).denied("USER_MANAGEMENT", "USER_ROLES_UPDATED", "USER", 7L, null, "禁止移除最后一个超级管理员角色");
    }

    @Test
    void administratorStatusChangeWritesCandidateVisibleBusinessSummary() {
        UserAccount candidate = user(18L, null, 1);
        when(currentUser.id()).thenReturn(99L);
        when(userMapper.selectById(18L)).thenReturn(candidate);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        service.updateStatus(18L, new UserDtos.UpdateStatusRequest(0));

        verify(userMapper).updateById(candidate);
        assertEquals(0, candidate.getStatus());
        assertEquals(1, candidate.getSecurityVersion());
        verify(auditService).success("USER_MANAGEMENT", "USER_STATUS_UPDATED", "USER", 18L, null,
                "管理员停用账号");
    }

    @Test
    void companyRoleMustHaveCompanyBinding() {
        when(userMapper.exists(any())).thenReturn(false);
        when(roleMapper.selectBatchIds(eq(List.of(30L)))).thenReturn(List.of(role(30L, "COMPANY_ADMIN")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(
                new UserDtos.CreateUserRequest("company_admin", "Password123", "Company Admin", null, "13800000000", null, List.of(30L))));

        assertEquals(400, exception.getStatus().value());
        verify(userMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void candidateCannotAlsoReceiveAdministratorRole() {
        UserAccount candidate = user(10L, null, 1);
        when(userMapper.selectById(10L)).thenReturn(candidate);
        when(roleMapper.selectBatchIds(eq(List.of(1L, 2L))))
                .thenReturn(List.of(role(1L, "ADMIN"), role(2L, "CANDIDATE")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(10L, new UserDtos.AssignRolesRequest(List.of(1L, 2L))));

        assertEquals(400, exception.getStatus().value());
        assertEquals("候选人角色必须单独分配，不能同时拥有平台或企业角色", exception.getMessage());
        verify(userRoleMapper, never()).delete(any());
        verify(userMapper, never()).updateById(any(UserAccount.class));
    }

    @Test
    void changedRolesIncrementSecurityVersion() {
        UserAccount user = user(12L, null, 1);
        user.setSecurityVersion(3);
        when(currentUser.id()).thenReturn(99L);
        when(userMapper.selectById(12L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(12L, 2L)));
        when(roleMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") List<Long> ids = invocation.getArgument(0);
            return ids.contains(6L) ? List.of(role(6L, "HR")) : List.of(role(2L, "CANDIDATE"));
        });

        service.assignRoles(12L, new UserDtos.AssignRolesRequest(List.of(6L)));

        assertEquals(4, user.getSecurityVersion());
        verify(userMapper).updateById(user);
        verify(refreshTokenService).revokeAllSessions(12L, "ROLES_CHANGED");
    }

    @Test
    void platformAdminCannotRemoveTheLastCompanyAdminRole() {
        UserAccount companyAdmin = user(20L, 100L, 1);
        Company company = new Company();
        company.setId(100L);
        company.setStatus(1);
        when(userMapper.selectById(20L)).thenReturn(companyAdmin);
        when(userMapper.selectList(any())).thenReturn(List.of(companyAdmin));
        when(companyMapper.selectById(100L)).thenReturn(company);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(20L, 10L)));
        when(roleMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") List<Long> ids = invocation.getArgument(0);
            return ids.contains(11L) ? List.of(role(11L, "COMPANY_RECRUITER"))
                    : List.of(role(10L, "COMPANY_ADMIN"));
        });
        when(roleMapper.selectOne(any())).thenReturn(role(10L, "COMPANY_ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(20L, new UserDtos.AssignRolesRequest(List.of(11L))));

        assertEquals(409, exception.getStatus().value());
        verify(userRoleMapper, never()).delete(any());
        verify(userMapper, never()).updateById(any(UserAccount.class));
    }

    private UserAccount user(Long id, Long companyId, int status) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setStatus(status);
        return user;
    }

    private UserRole userRole(Long userId, Long roleId) {
        UserRole relation = new UserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(1);
        role.setVersion(0);
        return role;
    }
}
