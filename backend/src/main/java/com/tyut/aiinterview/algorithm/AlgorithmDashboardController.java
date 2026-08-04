package com.tyut.aiinterview.algorithm;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/algorithm")
public class AlgorithmDashboardController {
    private final AlgorithmProgressService progressService;
    private final AlgorithmSubmissionService submissionService;
    private final CurrentUser currentUser;

    public AlgorithmDashboardController(AlgorithmProgressService progressService,
                                        AlgorithmSubmissionService submissionService,
                                        CurrentUser currentUser) {
        this.progressService = progressService;
        this.submissionService = submissionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AlgorithmDtos.DashboardView> dashboard() {
        return ApiResponse.ok(progressService.dashboard(currentUser.id()));
    }

    @GetMapping("/wrong-problems")
    public ApiResponse<List<AlgorithmDtos.WrongProblemView>> wrongProblems() {
        return ApiResponse.ok(submissionService.wrongProblems(currentUser.id()));
    }
}
