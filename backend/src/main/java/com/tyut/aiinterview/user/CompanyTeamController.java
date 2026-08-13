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
@RequestMapping("/v1/company/team")
public class CompanyTeamController {
    private final CompanyTeamService service;

    public CompanyTeamController(CompanyTeamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@companyAccessService.hasPermission('company:read')")
    public ApiResponse<List<CompanyTeamDtos.TeamMemberView>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("@companyAccessService.hasPermission('company:team:manage')")
    public ApiResponse<CompanyTeamDtos.TeamMemberView> create(
            @Valid @RequestBody CompanyTeamDtos.TeamCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("@companyAccessService.hasPermission('company:team:manage')")
    public ApiResponse<CompanyTeamDtos.TeamMemberView> roles(
            @PathVariable Long userId, @Valid @RequestBody CompanyTeamDtos.TeamRoleRequest request) {
        return ApiResponse.ok(service.updateRoles(userId, request));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("@companyAccessService.hasPermission('company:team:manage')")
    public ApiResponse<Void> status(
            @PathVariable Long userId, @Valid @RequestBody CompanyTeamDtos.TeamStatusRequest request) {
        service.updateStatus(userId, request);
        return ApiResponse.ok();
    }
}
