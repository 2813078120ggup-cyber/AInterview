package com.tyut.aiinterview.user;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleManagementController {
    private final RoleManagementService service;
    public RoleManagementController(RoleManagementService service) { this.service = service; }
    @GetMapping public ApiResponse<List<RoleDtos.RoleVO>> roles() { return ApiResponse.ok(service.roles()); }
    @GetMapping("/permissions") public ApiResponse<List<RoleDtos.PermissionVO>> permissions() { return ApiResponse.ok(service.permissions()); }
    @PostMapping public ApiResponse<RoleDtos.RoleVO> create(@Valid @RequestBody RoleDtos.RoleRequest request) { return ApiResponse.ok(service.create(request)); }
    @PutMapping("/{id}") public ApiResponse<RoleDtos.RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleDtos.RoleRequest request) { return ApiResponse.ok(service.update(id, request)); }
    @PutMapping("/{id}/permissions") public ApiResponse<RoleDtos.RoleVO> permissions(@PathVariable Long id, @Valid @RequestBody RoleDtos.AssignPermissionsRequest request) { return ApiResponse.ok(service.assignPermissions(id, request)); }
}
