package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/recruitment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecruitmentController {
    private final AdminRecruitmentService service;

    public AdminRecruitmentController(AdminRecruitmentService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    public ApiResponse<PageResult<AdminRecruitmentDtos.ApplicationView>> applications(AdminRecruitmentDtos.Query query) {
        return ApiResponse.ok(service.page(query));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminRecruitmentDtos.Summary> summary(AdminRecruitmentDtos.Query query) {
        return ApiResponse.ok(service.summary(query));
    }

    @GetMapping("/applications/{id}")
    public ApiResponse<AdminRecruitmentDtos.Detail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<AdminRecruitmentDtos.TaskView> retry(@PathVariable Long taskId,
                                                             @Valid @RequestBody(required = false) RetryRequest request) {
        return ApiResponse.ok(service.retry(taskId, request == null ? null : request.confirm()));
    }

    public record RetryRequest(@NotNull Boolean confirm) {}
}
