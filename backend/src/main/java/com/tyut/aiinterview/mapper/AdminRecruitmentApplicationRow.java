package com.tyut.aiinterview.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Sanitized administrator recruitment projection. It deliberately contains
 * current associations and task metadata, never resume or position snapshots.
 */
@Data
public class AdminRecruitmentApplicationRow {
    private Long id;
    private String applicationNo;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private Long positionId;
    private String positionName;
    private String positionDepartment;
    private Long candidateId;
    private String candidateUsername;
    private String candidateRealName;
    private String status;
    private BigDecimal matchScore;
    private String matchStatus;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private Long interviewId;
    private String interviewType;
    private Integer interviewStatus;
    private LocalDateTime interviewScheduledAt;
    private LocalDateTime interviewStartedAt;
    private LocalDateTime interviewEndedAt;
    private Integer reportStatus;
    private LocalDateTime reportGeneratedAt;
    private LocalDateTime reportPublishedAt;
    private Long matchTaskId;
    private String matchTaskStatus;
    private Integer matchTaskAttempts;
    private Integer matchTaskMaxAttempts;
    private LocalDateTime matchTaskScheduledAt;
    private LocalDateTime matchTaskStartedAt;
    private LocalDateTime matchTaskFinishedAt;
    private Long reportTaskId;
    private String reportTaskStatus;
    private Integer reportTaskAttempts;
    private Integer reportTaskMaxAttempts;
    private LocalDateTime reportTaskScheduledAt;
    private LocalDateTime reportTaskStartedAt;
    private LocalDateTime reportTaskFinishedAt;
}
