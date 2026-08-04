package com.tyut.aiinterview.report;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.domain.Report;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/interviews/{interviewId}/report")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }
    @GetMapping public ApiResponse<ReportDtos.ReportDetail> get(@PathVariable Long interviewId) { return ApiResponse.ok(service.detail(interviewId)); }
    @PostMapping public ApiResponse<Report> generate(@PathVariable Long interviewId) { return ApiResponse.ok(service.generate(interviewId)); }
    @PostMapping("/publish") public ApiResponse<Report> publish(@PathVariable Long interviewId) { return ApiResponse.ok(service.publish(interviewId)); }
    @PostMapping("/training-plan") public ApiResponse<ReportDtos.TrainingPlan> trainingPlan(@PathVariable Long interviewId) { return ApiResponse.ok(service.trainingPlan(interviewId)); }
}
