package com.tyut.aiinterview.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class RoleDtos {
    private RoleDtos() {}
    public record RoleRequest(@NotBlank @Size(max = 32) String roleCode, @NotBlank @Size(max = 64) String roleName, String description, @NotNull Integer status) {}
    public record AssignPermissionsRequest(@NotEmpty List<Long> permissionIds) {}
    public record RoleVO(Long id, String roleCode, String roleName, String description, Integer status, List<Long> permissionIds) {}
    public record PermissionVO(Long id, String permissionCode, String permissionName, String resourceType, String description) {}
}
