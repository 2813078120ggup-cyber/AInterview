package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/workspace")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWorkspaceController {
    private final AdminWorkspaceService service;

    public AdminWorkspaceController(AdminWorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminWorkspaceDtos.Summary> summary() {
        return ApiResponse.ok(service.summary());
    }
}
