package com.tyut.aiinterview.notification;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {
    private final NotificationMailService notificationMailService;
    private final SiteNotificationService siteNotificationService;

    public NotificationController(NotificationMailService notificationMailService,
                                  SiteNotificationService siteNotificationService) {
        this.notificationMailService = notificationMailService;
        this.siteNotificationService = siteNotificationService;
    }

    @PostMapping("/mail-sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationDtos.MailSyncResponse> syncMail(@Valid @RequestBody NotificationDtos.MailSyncRequest request) {
        return ApiResponse.ok(notificationMailService.syncMail(request));
    }

    @PostMapping("/site")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> createSite(@Valid @RequestBody NotificationDtos.SiteCreateRequest request) {
        siteNotificationService.create(request.recipientId(), request.notificationType() == null ? "GENERAL" : request.notificationType(),
                request.title(), request.content(), request.businessType(), request.businessId(), request.dedupeKey());
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<NotificationDtos.NotificationPage> page(NotificationDtos.Query query) {
        return ApiResponse.ok(siteNotificationService.page(query));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(siteNotificationService.unreadCount());
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        siteNotificationService.markRead(id);
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAll() {
        siteNotificationService.markAllRead();
        return ApiResponse.ok();
    }
}
