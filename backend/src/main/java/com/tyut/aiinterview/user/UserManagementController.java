package com.tyut.aiinterview.user;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {
    private final UserManagementService service;
    public UserManagementController(UserManagementService service) { this.service = service; }
    @GetMapping public ApiResponse<PageResult<UserDtos.UserVO>> page(UserDtos.UserQuery query) { return ApiResponse.ok(service.page(query)); }
    @GetMapping("/candidates") public ApiResponse<java.util.List<UserDtos.UserOption>> candidates(@RequestParam(required = false) String keyword) { return ApiResponse.ok(service.candidates(keyword)); }
    @PostMapping public ApiResponse<UserDtos.UserVO> create(@Valid @RequestBody UserDtos.CreateUserRequest request) { return ApiResponse.ok(service.create(request)); }
    @PutMapping("/{id}/status") public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateStatusRequest request) { service.updateStatus(id, request); return ApiResponse.ok(); }
    @PutMapping("/{id}/roles") public ApiResponse<UserDtos.UserVO> roles(@PathVariable Long id, @Valid @RequestBody UserDtos.AssignRolesRequest request) { return ApiResponse.ok(service.assignRoles(id, request)); }
}
