package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_application")
public class JobApplication {
    private Long id;
    private String applicationNo;
    private Long companyId;
    private Long positionId;
    private Long candidateId;
    private Long resumeId;
    private Long interviewId;
    private String status;
    private String source;
    private BigDecimal matchScore;
    private String matchSummary;
    private String matchDetails;
    private String matchStatus;
    private Integer matchVersion;
    private Integer matchEvaluationVersion;
    private String matchError;
    private LocalDateTime matchStartedAt;
    private LocalDateTime matchCompletedAt;
    private String candidateMessage;
    private String reviewNote;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
