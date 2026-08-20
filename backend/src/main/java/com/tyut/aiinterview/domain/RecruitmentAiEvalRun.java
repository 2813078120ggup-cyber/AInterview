package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_eval_run")
public class RecruitmentAiEvalRun {
    private Long id;
    private Long suiteId;
    private String status;
    private String provider;
    private String model;
    private String promptCode;
    private Integer promptVersion;
    private Integer caseCount;
    private Integer passedCaseCount;
    private BigDecimal passRate;
    private BigDecimal maximumScoreDrift;
    private BigDecimal maximumFairnessGap;
    private String failureSummary;
    private Long startedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
