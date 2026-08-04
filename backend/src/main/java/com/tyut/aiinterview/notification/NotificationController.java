package com.tyut.aiinterview.notification;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {
    private final NotificationMailService notificationMailService;

    public NotificationController(NotificationMailService notificationMailService) {
        this.notificationMailService = notificationMailService;
    }

    @PostMapping("/mail-sync")
    public ApiResponse<NotificationDtos.MailSyncResponse> syncMail(@Valid @RequestBody NotificationDtos.MailSyncRequest request) {
        return ApiResponse.ok(notificationMailService.syncMail(request));
    }
}
