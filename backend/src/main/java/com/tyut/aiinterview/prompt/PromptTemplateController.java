package com.tyut.aiinterview.prompt;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/prompt-templates")
@PreAuthorize("hasRole('ADMIN')")
public class PromptTemplateController {
    private final PromptTemplateService service;

    public PromptTemplateController(PromptTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<PromptDtos.PromptSummary>> list() {
        return ApiResponse.ok(service.listSummaries());
    }

    @GetMapping("/{code}")
    public ApiResponse<PromptDtos.PromptDetail> detail(@PathVariable String code) {
        return ApiResponse.ok(service.detail(code));
    }

    @PostMapping("/{code}/versions")
    public ApiResponse<PromptDtos.VersionView> createVersion(@PathVariable String code,
                                                              @Valid @RequestBody PromptDtos.CreateVersionRequest request) {
        return ApiResponse.ok(service.createVersion(code, request));
    }

    @PostMapping("/{code}/versions/{version}/activate")
    public ApiResponse<PromptDtos.VersionView> activate(@PathVariable String code, @PathVariable int version,
                                                         @Valid @RequestBody(required = false) PromptDtos.ActivationRequest request) {
        return ApiResponse.ok(service.activate(code, version, request));
    }

    @PostMapping("/{code}/versions/{version}/rollback")
    public ApiResponse<PromptDtos.VersionView> rollback(@PathVariable String code, @PathVariable int version,
                                                         @Valid @RequestBody(required = false) PromptDtos.ActivationRequest request) {
        return ApiResponse.ok(service.rollback(code, version, request));
    }
}
