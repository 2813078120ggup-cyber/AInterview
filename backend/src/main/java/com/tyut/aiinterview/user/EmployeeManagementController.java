package com.tyut.aiinterview.user;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeManagementController {
    private final UserManagementService service;

    public EmployeeManagementController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<UserDtos.UserVO>> page(UserDtos.UserQuery query) {
        return ApiResponse.ok(service.employeePage(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDtos.UserVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.employeeDetail(id));
    }
}
