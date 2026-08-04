package com.tyut.aiinterview.algorithm;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/algorithm")
public class AlgorithmSubmissionController {
    private final AlgorithmSubmissionService service;
    private final CurrentUser currentUser;

    public AlgorithmSubmissionController(AlgorithmSubmissionService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/run")
    public ApiResponse<AlgorithmDtos.RunResponse> run(@RequestBody AlgorithmDtos.RunRequest request) {
        return ApiResponse.ok(service.run(currentUser.id(), request));
    }

    @PostMapping("/submit")
    public ApiResponse<Long> submit(@RequestBody AlgorithmDtos.SubmitRequest request) {
        return ApiResponse.ok(service.submit(currentUser.id(), request));
    }

    @GetMapping("/submissions")
    public ApiResponse<PageResult<AlgorithmDtos.SubmissionListItem>> list(
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(currentUser.id(), problemId, status, page, pageSize));
    }

    @GetMapping("/submissions/{submissionId}")
    public ApiResponse<AlgorithmDtos.SubmissionDetailView> detail(@PathVariable Long submissionId) {
        return ApiResponse.ok(service.detail(currentUser.id(), submissionId));
    }
}
