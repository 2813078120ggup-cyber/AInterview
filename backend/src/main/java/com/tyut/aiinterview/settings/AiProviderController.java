package com.tyut.aiinterview.settings;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/admin/ai-providers")
@PreAuthorize("hasRole('ADMIN')")
public class AiProviderController {
    private final AiProviderService service;

    public AiProviderController(AiProviderService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AiProviderDtos.ProviderView>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<AiProviderDtos.ProviderView> create(@Valid @RequestBody AiProviderDtos.ProviderRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiProviderDtos.ProviderView> update(@PathVariable Long id, @Valid @RequestBody AiProviderDtos.ProviderRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/test")
    public ApiResponse<AiProviderDtos.ProviderTestResult> test(@PathVariable Long id) {
        return ApiResponse.ok(service.test(id));
    }
}
