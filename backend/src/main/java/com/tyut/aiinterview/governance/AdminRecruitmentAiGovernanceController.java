package com.tyut.aiinterview.governance;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/ai-recruitment-governance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecruitmentAiGovernanceController {
    private final AdminRecruitmentAiGovernanceService service;

    public AdminRecruitmentAiGovernanceController(AdminRecruitmentAiGovernanceService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<RecruitmentAiGovernanceDtos.Overview> overview() {
        return ApiResponse.ok(service.overview());
    }

    @PutMapping("/policy/global")
    public ApiResponse<RecruitmentAiGovernanceDtos.PolicyView> updateGlobal(
            @Valid @RequestBody RecruitmentAiGovernanceDtos.PolicyUpdate request) {
        return ApiResponse.ok(service.updateGlobal(request));
    }

    @PutMapping("/policy/companies/{companyId}")
    public ApiResponse<RecruitmentAiGovernanceDtos.PolicyView> updateTenant(
            @PathVariable Long companyId,
            @Valid @RequestBody RecruitmentAiGovernanceDtos.PolicyUpdate request) {
        return ApiResponse.ok(service.updateTenant(companyId, request));
    }

    @DeleteMapping("/policy/companies/{companyId}")
    public ApiResponse<Void> resetTenant(@PathVariable Long companyId) {
        service.resetTenant(companyId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/emergency-stop")
    public ApiResponse<RecruitmentAiGovernanceDtos.PolicyView> emergency(
            @Valid @RequestBody RecruitmentAiGovernanceDtos.EmergencyStopRequest request) {
        return ApiResponse.ok(service.emergency(request));
    }

    @GetMapping("/evaluation-suites/{suiteId}/cases")
    public ApiResponse<List<RecruitmentAiGovernanceDtos.EvalCaseView>> cases(@PathVariable Long suiteId) {
        return ApiResponse.ok(service.cases(suiteId));
    }

    @PostMapping("/evaluation-suites/{suiteId}/cases")
    public ApiResponse<RecruitmentAiGovernanceDtos.EvalCaseView> createCase(
            @PathVariable Long suiteId,
            @Valid @RequestBody RecruitmentAiGovernanceDtos.EvalCaseRequest request) {
        return ApiResponse.ok(service.createCase(suiteId, request));
    }

    @PostMapping("/evaluation-suites/{suiteId}/runs")
    public ApiResponse<RecruitmentAiGovernanceDtos.EvalRunView> startRun(@PathVariable Long suiteId) {
        var run = service.startRun(suiteId);
        return ApiResponse.ok(service.run(run.getId()));
    }

    @GetMapping("/evaluation-runs/{id}")
    public ApiResponse<RecruitmentAiGovernanceDtos.EvalRunView> run(@PathVariable Long id) {
        return ApiResponse.ok(service.run(id));
    }
}
