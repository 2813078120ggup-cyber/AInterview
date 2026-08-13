package com.tyut.aiinterview.account;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account/notification-preferences")
@PreAuthorize("hasRole('CANDIDATE')")
public class AccountNotificationPreferenceController {
    private final AccountNotificationPreferenceService service;

    public AccountNotificationPreferenceController(AccountNotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AccountNotificationPreferenceDtos.Preferences> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<AccountNotificationPreferenceDtos.Preferences> update(
            @Valid @RequestBody AccountNotificationPreferenceDtos.UpdateRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
