package com.tyut.aiinterview.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminWorkspaceDtos {
    private AdminWorkspaceDtos() {}

    public record Summary(LocalDate periodStart, LocalDate periodEnd, LocalDateTime generatedAt,
                          Metrics metrics, WorkerStatus worker, List<ActionItem> actions) {}

    public record Metrics(long companyCount, long activeUserCount, long recruitingPositionCount,
                          long weeklyApplicationCount, long inProgressInterviewCount,
                          long reportBacklogCount, long aiFailedTaskCount, long pendingTicketCount) {}

    public record WorkerStatus(String code, String label, String summary, String recommendation,
                               long queuedCount, long runningCount, LocalDateTime oldestQueuedAt) {}

    public record ActionItem(String type, String label, String description, String recommendation,
                             long count, String severity, String targetPath) {}
}
