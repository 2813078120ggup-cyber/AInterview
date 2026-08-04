package com.tyut.aiinterview.ai;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/ai-generations")
@PreAuthorize("hasRole('ADMIN')")
public class AiGenerationAuditController {
    private final AiGenerationAuditService service;

    public AiGenerationAuditController(AiGenerationAuditService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<AiGenerationAuditDtos.RecordView>> page(AiGenerationAuditDtos.Query query) {
        return ApiResponse.ok(service.page(query));
    }

    @GetMapping("/summary")
    public ApiResponse<AiGenerationAuditDtos.Summary> summary(AiGenerationAuditDtos.Query query) {
        return ApiResponse.ok(service.summary(query));
    }
}
