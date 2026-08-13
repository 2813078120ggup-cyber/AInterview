package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminAiOpsTaskSummaryRow {
    private Long pending;
    private Long running;
    private Long failed;
    private Long backlog;
    private Long reportBacklog;
    private LocalDateTime oldestPendingAt;
}
