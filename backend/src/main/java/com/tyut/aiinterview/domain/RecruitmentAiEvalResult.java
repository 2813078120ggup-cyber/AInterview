package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_eval_result")
public class RecruitmentAiEvalResult {
    private Long id;
    private Long runId;
    private Long caseId;
    private String status;
    private BigDecimal actualScore;
    private BigDecimal scoreDrift;
    private String responseHash;
    private String assertionSummary;
    private String errorMessage;
    private Long latencyMs;
    private LocalDateTime createdAt;
}
