package com.tyut.aiinterview.user;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/company/settings")
public class CompanySettingsController {
    private final CompanySettingsService service;

    public CompanySettingsController(CompanySettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@companyAccessService.hasPermission('company:read')")
    public ApiResponse<CompanySettingsDtos.SettingsView> view() {
        return ApiResponse.ok(service.view());
    }

    @PutMapping
    @PreAuthorize("@companyAccessService.hasPermission('company:write')")
    public ApiResponse<CompanySettingsDtos.SettingsView> update(
            @Valid @RequestBody CompanySettingsDtos.UpdateRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
