package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/ai-operations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiOperationsController {
    private final AdminAiOperationsService service;

    public AdminAiOperationsController(AdminAiOperationsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminAiOperationsDtos.Overview> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/traces/generations/{id}")
    public ApiResponse<AdminAiOperationsDtos.Trace> trace(@PathVariable Long id) {
        return ApiResponse.ok(service.trace(id));
    }

    @PostMapping("/tasks/{id}/retry")
    public ApiResponse<AdminAiOperationsDtos.TaskView> retry(@PathVariable Long id,
                                                              @RequestBody(required = false) RetryRequest request) {
        return ApiResponse.ok(service.retry(id, request == null ? false : request.confirm()));
    }

    public record RetryRequest(@NotNull Boolean confirm) {
    }
}
