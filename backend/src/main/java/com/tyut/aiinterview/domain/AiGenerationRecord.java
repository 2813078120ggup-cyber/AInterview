package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_generation_record")
public class AiGenerationRecord {
    private Long id;
    private String requestId;
    private Long taskId;
    private Long interviewId;
    private Long freeInterviewSessionId;
    private Long companyId;
    private String generationType;
    private String governanceScope;
    private Long governancePolicyId;
    private Long costReservationId;
    private String promptCode;
    private Integer promptVersionNo;
    private String provider;
    private String model;
    private String status;
    private Long latencyMs;
    private Integer inputChars;
    private Integer outputChars;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal estimatedCostUsd;
    private BigDecimal actualCostUsd;
    private Integer httpStatus;
    private String errorType;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
