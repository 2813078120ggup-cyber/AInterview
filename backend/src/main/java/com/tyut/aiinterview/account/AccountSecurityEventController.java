package com.tyut.aiinterview.account;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account/security-events")
@PreAuthorize("hasRole('CANDIDATE')")
public class AccountSecurityEventController {
    private final AccountSecurityEventService service;

    public AccountSecurityEventController(AccountSecurityEventService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<AccountSecurityEventDtos.SecurityEvent>> page(
            @ModelAttribute AccountSecurityEventDtos.Query query) {
        return ApiResponse.ok(service.page(query));
    }
}
