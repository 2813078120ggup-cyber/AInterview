package com.tyut.aiinterview.algorithm;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.security.CurrentUser;
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
@RequestMapping("/algorithm/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAlgorithmController {
    private final AlgorithmProblemService service;
    private final CurrentUser currentUser;

    public AdminAlgorithmController(AlgorithmProblemService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/problems")
    public ApiResponse<List<AlgorithmDtos.AdminProblemView>> list() {
        return ApiResponse.ok(service.adminList());
    }

    @GetMapping("/problems/{problemId}")
    public ApiResponse<AlgorithmDtos.AdminProblemDetailView> detail(@PathVariable Long problemId) {
        return ApiResponse.ok(service.adminDetail(problemId));
    }

    @PostMapping("/problems")
    public ApiResponse<Long> create(@RequestBody AlgorithmDtos.AdminProblemSaveRequest request) {
        return ApiResponse.ok(service.adminSave(currentUser.id(), request));
    }

    @PutMapping("/problems/{problemId}")
    public ApiResponse<Void> update(@PathVariable Long problemId,
                                    @RequestBody AlgorithmDtos.AdminProblemSaveRequest request) {
        service.adminUpdate(problemId, request);
        return ApiResponse.ok();
    }

    @PutMapping("/problems/{problemId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long problemId,
                                          @RequestBody AlgorithmDtos.StatusRequest request) {
        service.adminUpdateStatus(problemId, request.status());
        return ApiResponse.ok();
    }
}
