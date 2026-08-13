package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
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
@RequestMapping("/v1/admin/companies")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompanyController {
    private final AdminCompanyService service;

    public AdminCompanyController(AdminCompanyService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<AdminCompanyDtos.CompanyView>> page(AdminCompanyDtos.Query query) {
        return ApiResponse.ok(service.page(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminCompanyDtos.CompanyDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    public ApiResponse<AdminCompanyDtos.CompanyDetailView> create(
            @Valid @RequestBody AdminCompanyDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCompanyDtos.CompanyDetailView> update(
            @PathVariable Long id, @Valid @RequestBody AdminCompanyDtos.UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<AdminCompanyDtos.CompanyDetailView> status(
            @PathVariable Long id, @Valid @RequestBody AdminCompanyDtos.StatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<AdminCompanyDtos.MemberView>> members(@PathVariable Long id) {
        return ApiResponse.ok(service.members(id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<AdminCompanyDtos.MemberView> createMember(
            @PathVariable Long id, @Valid @RequestBody AdminCompanyDtos.MemberCreateRequest request) {
        return ApiResponse.ok(service.createMember(id, request));
    }
}
