package com.tyut.aiinterview.mapper;

import lombok.Data;

@Data
public class AdminAiOpsGenerationSummaryRow {
    private Long total;
    private Long success;
    private Long failed;
    private Long running;
    private Long averageLatencyMs;
    private Long totalTokens;
}
