package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminWorkspaceSummaryRow {
    private Long companyCount;
    private Long activeUserCount;
    private Long recruitingPositionCount;
    private Long weeklyApplicationCount;
    private Long inProgressInterviewCount;
    private Long reportBacklogCount;
    private Long aiFailedTaskCount;
    private Long pendingTicketCount;
    private Long algorithmQueuedCount;
    private Long algorithmRunningCount;
    private LocalDateTime algorithmOldestQueuedAt;
}
