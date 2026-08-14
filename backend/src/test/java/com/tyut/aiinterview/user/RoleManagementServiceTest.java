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
import com.tyut.aiinterview.domain.Permission;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.RolePermission;
import com.tyut.aiinterview.mapper.AdminUserMapper;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleManagementServiceTest {
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
    private final AdminUserMapper adminUserMapper = mock(AdminUserMapper.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final RoleManagementService service = new RoleManagementService(roleMapper, permissionMapper, rolePermissionMapper, adminUserMapper, auditService);

    @Test
    void protectsSystemRoleCodeFromArbitraryEdits() {
        Role admin = role(1L, "ADMIN", 0);
        when(roleMapper.selectById(1L)).thenReturn(admin);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(1L,
                new RoleDtos.RoleRequest("ADMIN", "Platform admin", "changed", 1, 0)));

        assertEquals(403, exception.getStatus().value());
        verify(roleMapper, never()).updateWithVersion(any(Role.class));
        verify(auditService).denied("AUTHORIZATION", "ROLE_UPDATED", "ROLE", 1L, null, "系统角色受保护");
    }

    @Test
    void requiresImpactConfirmationBeforeChangingPermissions() {
        Role role = role(8L, "CUSTOM_REVIEWER", 4);
        when(roleMapper.selectById(8L)).thenReturn(role);
        when(permissionMapper.selectBatchIds(eq(List.of(11L)))).thenReturn(List.of(permission(11L)));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
        when(adminUserMapper.countUsersByRoleId(8L)).thenReturn(3L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assignPermissions(8L,
                new RoleDtos.AssignPermissionsRequest(List.of(11L), 4, false)));

        assertEquals(409, exception.getStatus().value());
        verify(roleMapper, never()).bumpVersion(8L, 4);
        verify(auditService).denied("AUTHORIZATION", "ROLE_PERMISSIONS_UPDATED", "ROLE", 8L, null, "权限变更影响 3 个用户，未确认");
    }

    @Test
    void rejectsStalePermissionVersion() {
        Role role = role(8L, "CUSTOM_REVIEWER", 4);
        when(roleMapper.selectById(8L)).thenReturn(role);
        when(permissionMapper.selectBatchIds(eq(List.of(11L)))).thenReturn(List.of(permission(11L)));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
        when(adminUserMapper.countUsersByRoleId(8L)).thenReturn(0L);
        when(roleMapper.bumpVersion(8L, 4)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assignPermissions(8L,
                new RoleDtos.AssignPermissionsRequest(List.of(11L), 4, true)));

        assertEquals(409, exception.getStatus().value());
        verify(rolePermissionMapper, never()).delete(any());
        verify(auditService).denied("AUTHORIZATION", "ROLE_PERMISSIONS_UPDATED", "ROLE", 8L, null, "角色版本冲突");
    }

    @Test
    void candidateIdentityCannotReceiveBackendPermissions() {
        Role candidate = role(2L, "CANDIDATE", 1);
        when(roleMapper.selectById(2L)).thenReturn(candidate);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assignPermissions(2L,
                new RoleDtos.AssignPermissionsRequest(List.of(11L), 1, true)));

        assertEquals(403, exception.getStatus().value());
        assertEquals("候选人能力由身份和本人资源边界固定控制，不支持分配后台权限", exception.getMessage());
        verify(permissionMapper, never()).selectBatchIds(any());
        verify(rolePermissionMapper, never()).delete(any());
        verify(auditService).denied("AUTHORIZATION", "ROLE_PERMISSIONS_UPDATED", "ROLE", 2L, null,
                "候选人身份不参与后台权限矩阵");
    }

    @Test
    void candidateRoleNeverExposesStalePermissionRelations() {
        Role candidate = role(2L, "CANDIDATE", 1);
        when(roleMapper.selectList(any())).thenReturn(List.of(candidate));
        when(adminUserMapper.countUsersByRoleId(2L)).thenReturn(5L);

        RoleDtos.RoleVO result = service.roles().get(0);

        assertEquals(List.of(), result.permissionIds());
        verify(rolePermissionMapper, never()).selectList(any());
    }

    private Role role(Long id, String code, int version) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(1);
        role.setVersion(version);
        return role;
    }

    private Permission permission(Long id) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setPermissionCode("review:read");
        return permission;
    }
}
