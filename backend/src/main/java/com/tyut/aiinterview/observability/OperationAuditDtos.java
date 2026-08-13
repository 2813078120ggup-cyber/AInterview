package com.tyut.aiinterview.observability;

import java.time.LocalDateTime;

public final class OperationAuditDtos {
    private OperationAuditDtos() {
    }

    public record Query(Long pageNo, Long pageSize, String keyword, String module, String action,
                        String resourceType, String result, Long actorId, Long companyId,
                        String from, String to) {
    }

    public record View(Long id, String requestId, Long actorId, String actorRole, Long companyId,
                       String module, String action, String resourceType, String resourceId,
                       String result, String summary, String ipAddress, String userAgent,
                       LocalDateTime createdAt) {
    }
}
