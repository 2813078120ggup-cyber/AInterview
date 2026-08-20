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
    private final AdminRecruitmentRequisitionService requisitionService;

    public AdminRecruitmentController(AdminRecruitmentService service,
                                      AdminRecruitmentRequisitionService requisitionService) {
        this.service = service;
        this.requisitionService = requisitionService;
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

    @GetMapping("/requisitions")
    public ApiResponse<PageResult<AdminRecruitmentDtos.RequisitionView>> requisitions(
            AdminRecruitmentDtos.RequisitionQuery query) {
        return ApiResponse.ok(requisitionService.page(query));
    }

    @GetMapping("/requisitions/{id}")
    public ApiResponse<AdminRecruitmentDtos.RequisitionDetail> requisition(@PathVariable Long id) {
        return ApiResponse.ok(requisitionService.detail(id));
    }

    @PostMapping("/requisitions/{id}/approve")
    public ApiResponse<AdminRecruitmentDtos.RequisitionDetail> approveRequisition(
            @PathVariable Long id, @Valid @RequestBody AdminRecruitmentDtos.ApprovalRequest request) {
        return ApiResponse.ok(requisitionService.approve(id, request));
    }

    @PostMapping("/requisitions/{id}/reject")
    public ApiResponse<AdminRecruitmentDtos.RequisitionDetail> rejectRequisition(
            @PathVariable Long id, @Valid @RequestBody AdminRecruitmentDtos.DecisionRequest request) {
        return ApiResponse.ok(requisitionService.reject(id, request));
    }

    @PostMapping("/requisitions/{id}/freeze")
    public ApiResponse<AdminRecruitmentDtos.RequisitionDetail> freezeRequisition(
            @PathVariable Long id, @Valid @RequestBody AdminRecruitmentDtos.DecisionRequest request) {
        return ApiResponse.ok(requisitionService.freeze(id, request));
    }

    @PostMapping("/requisitions/{id}/unfreeze")
    public ApiResponse<AdminRecruitmentDtos.RequisitionDetail> unfreezeRequisition(
            @PathVariable Long id, @Valid @RequestBody AdminRecruitmentDtos.DecisionRequest request) {
        return ApiResponse.ok(requisitionService.unfreeze(id, request));
    }

    public record RetryRequest(@NotNull Boolean confirm) {}
}
