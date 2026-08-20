package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_cost_reservation")
public class RecruitmentAiCostReservation {
    private Long id;
    private Long companyId;
    private String generationType;
    private String promptCode;
    private Integer promptVersion;
    private String provider;
    private String model;
    private String status;
    private Integer estimatedInputTokens;
    private Integer estimatedOutputTokens;
    private BigDecimal estimatedCostUsd;
    private Integer actualTokens;
    private BigDecimal actualCostUsd;
    private String generationRequestId;
    private LocalDateTime createdAt;
    private LocalDateTime settledAt;
}
