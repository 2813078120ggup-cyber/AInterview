package com.tyut.aiinterview.settings;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class PlatformUiSettingsController {
    private final PlatformUiSettingsService service;

    public PlatformUiSettingsController(PlatformUiSettingsService service) {
        this.service = service;
    }

    @GetMapping("/platform/ui-settings")
    public ApiResponse<PlatformUiSettingsDtos.View> read() {
        return ApiResponse.ok(service.read());
    }

    @PutMapping("/admin/platform/ui-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PlatformUiSettingsDtos.View> update(
            @Valid @RequestBody PlatformUiSettingsDtos.UpdateRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
