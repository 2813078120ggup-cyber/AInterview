package com.tyut.aiinterview.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record MailSyncRequest(
            @NotNull Long candidateId,
            String candidateUsername,
            @NotBlank String title,
            @NotBlank String content,
            String interviewTitle,
            String scheduledAt) {}

    public record MailSyncResponse(boolean sent, String email) {}

    public record SiteCreateRequest(@NotNull Long recipientId, @NotBlank String title,
                                    @NotBlank String content, String notificationType,
                                    String businessType, Long businessId, String dedupeKey) {}

    public record Query(Long pageNo, Long pageSize) {}
    public record Notification(Long id, String notificationType, String title, String content,
                               String businessType, Long businessId, String actionPath,
                               boolean read, LocalDateTime createdAt) {}
    public record NotificationPage(List<Notification> records, long total, long pageNo, long pageSize) {}
}
