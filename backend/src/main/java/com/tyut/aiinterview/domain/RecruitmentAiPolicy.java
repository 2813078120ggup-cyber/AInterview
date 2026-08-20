package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_policy")
public class RecruitmentAiPolicy {
    private Long id;
    private String scopeKey;
    private Long companyId;
    private Integer aiEnabled;
    private Integer emergencyStop;
    private String emergencyReason;
    private Integer evaluationGateRequired;
    private Integer evaluationValidDays;
    private BigDecimal minimumPassRate;
    private BigDecimal maximumScoreDrift;
    private BigDecimal maximumFairnessGap;
    private String humanReviewMode;
    private BigDecimal adverseScoreThreshold;
    private String sensitiveDataMode;
    private BigDecimal dailyCostLimitUsd;
    private BigDecimal monthlyCostLimitUsd;
    private BigDecimal inputCostPerMillionUsd;
    private BigDecimal outputCostPerMillionUsd;
    private Integer perRequestTokenLimit;
    private Integer version;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
