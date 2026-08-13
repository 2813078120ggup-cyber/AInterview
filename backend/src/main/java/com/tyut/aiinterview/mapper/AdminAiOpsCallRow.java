package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminAiOpsCallRow {
    private Long id;
    private String requestId;
    private Long taskId;
    private Long interviewId;
    private Long freeInterviewSessionId;
    private String generationType;
    private String promptCode;
    private Integer promptVersion;
    private String provider;
    private String model;
    private String status;
    private Long latencyMs;
    private Integer inputChars;
    private Integer outputChars;
    private Integer totalTokens;
    private Integer httpStatus;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
