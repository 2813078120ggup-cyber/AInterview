package com.tyut.aiinterview.reflection;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class InterviewReflectionController {
    private final InterviewReflectionService service;

    public InterviewReflectionController(InterviewReflectionService service) {
        this.service = service;
    }

    @GetMapping("/interviews/{interviewId}/reflection")
    public ApiResponse<ReflectionDtos.ReflectionView> get(@PathVariable Long interviewId) {
        return ApiResponse.ok(service.get(interviewId));
    }

    @PutMapping("/interviews/{interviewId}/reflection")
    public ApiResponse<ReflectionDtos.ReflectionView> save(@PathVariable Long interviewId,
                                                           @Valid @RequestBody ReflectionDtos.SaveRequest request) {
        return ApiResponse.ok(service.save(interviewId, request));
    }

    @GetMapping("/reflections/my/summary")
    public ApiResponse<ReflectionDtos.CandidateSummary> mine() {
        return ApiResponse.ok(service.mine());
    }
}
