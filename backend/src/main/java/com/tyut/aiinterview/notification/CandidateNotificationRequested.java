package com.tyut.aiinterview.notification;

import com.tyut.aiinterview.account.CandidateNotificationEvent;

public record CandidateNotificationRequested(
        Long recipientId,
        String notificationType,
        CandidateNotificationEvent event,
        String title,
        String content,
        String businessType,
        Long businessId,
        String dedupeKey) {
}
