package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_eval_case")
public class RecruitmentAiEvalCase {
    private Long id;
    private Long suiteId;
    private String caseCode;
    private String name;
    private String cohortCode;
    private String pairKey;
    private String inputJson;
    private BigDecimal expectedScoreMin;
    private BigDecimal expectedScoreMax;
    private BigDecimal baselineScore;
    private String requiredTerms;
    private String forbiddenTerms;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
