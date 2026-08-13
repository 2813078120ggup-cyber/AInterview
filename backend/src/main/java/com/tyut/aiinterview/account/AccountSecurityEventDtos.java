package com.tyut.aiinterview.account;

import java.time.LocalDateTime;

public final class AccountSecurityEventDtos {
    private AccountSecurityEventDtos() {
    }

    public record Query(Long pageNo, Long pageSize) {
    }

    public record SecurityEvent(
            String eventType,
            String result,
            String summary,
            String maskedIp,
            String deviceSummary,
            LocalDateTime createdAt) {
    }
}
