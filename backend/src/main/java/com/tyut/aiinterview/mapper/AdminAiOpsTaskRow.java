package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminAiOpsTaskRow {
    private Long id;
    private String taskType;
    private String status;
    private Integer attempts;
    private Integer maxAttempts;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long interviewId;
    private Long answerId;
    private LocalDateTime createdAt;
    private Long generationId;
    private String generationRequestId;
    private String provider;
    private String model;
    private String promptCode;
    private Integer promptVersion;
}
