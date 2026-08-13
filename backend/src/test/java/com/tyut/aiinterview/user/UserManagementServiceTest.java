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
    private final UserManagementService service = new UserManagementService(userMapper, userRoleMapper, roleMapper,
            passwordEncoder, currentUser, companyMapper, adminUserMapper, auditService);

    @Test
    void appliesRoleCompanyStatusAndCreatedDateFiltersInDatabaseQuery() {
        AdminUserRow row = new AdminUserRow();
        row.setId(9L);
        row.setUsername("recruiter");
        row.setRealName("Recruiter");
        row.setStatus(1);
        when(adminUserMapper.selectPage(eq("recruit"), eq("COMPANY_RECRUITER"), eq(88L), eq(1),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 9, 1, 0, 0)), eq(20L), eq(20L)))
                .thenReturn(List.of(row));
        when(adminUserMapper.count(eq("recruit"), eq("COMPANY_RECRUITER"), eq(88L), eq(1),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 9, 1, 0, 0))))
                .thenReturn(1L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        var result = service.page(new UserDtos.UserQuery(2L, 20L, "recruit", 1,
                "company_recruiter", 88L, "2026-08-01", "2026-08-31"));

        assertEquals(1L, result.total());
        assertEquals("recruiter", result.records().get(0).username());
        verify(adminUserMapper).selectPage(eq("recruit"), eq("COMPANY_RECRUITER"), eq(88L), eq(1),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 9, 1, 0, 0)), eq(20L), eq(20L));
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
