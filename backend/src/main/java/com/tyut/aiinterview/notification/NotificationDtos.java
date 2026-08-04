package com.tyut.aiinterview.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
}
