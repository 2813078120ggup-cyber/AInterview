package com.tyut.aiinterview.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class RoleDtos {
    private RoleDtos() {}

    public record RoleRequest(@NotBlank @Size(max = 32) String roleCode,
                              @NotBlank @Size(max = 64) String roleName,
                              String description, @NotNull Integer status, Integer version) {
        public RoleRequest(String roleCode, String roleName, String description, Integer status) {
            this(roleCode, roleName, description, status, null);
        }
    }

    public record AssignPermissionsRequest(@NotNull List<Long> permissionIds, Integer version, Boolean confirmImpact) {
        public AssignPermissionsRequest(List<Long> permissionIds) {
            this(permissionIds, null, true);
        }
    }

    public record RoleVO(Long id, String roleCode, String roleName, String description, Integer status,
                         List<Long> permissionIds, boolean protectedRole, long affectedUserCount, Integer version) {
        public RoleVO(Long id, String roleCode, String roleName, String description, Integer status, List<Long> permissionIds) {
            this(id, roleCode, roleName, description, status, permissionIds, false, 0, null);
        }
    }

    public record PermissionVO(Long id, String permissionCode, String permissionName, String resourceType, String description) {}
}
