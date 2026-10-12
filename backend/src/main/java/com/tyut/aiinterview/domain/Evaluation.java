package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("evaluation")
public class Evaluation {
    private Long id;
    private Long interviewQuestionId;
    private Long evaluatorId;
    private String source;
    private BigDecimal professionalScore;
    private BigDecimal expressionScore;
    private BigDecimal logicScore;
    private BigDecimal adaptabilityScore;
    private BigDecimal overallScore;
    private String comment;
    private Integer status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    // 语音分析感知维度（未评估时为 null）
    private BigDecimal voiceScore;
    private String voiceEvidence;
    private LocalDateTime voiceEvaluatedAt;
    // 文本理解与评价感知维度（未评估时为 null）
    private String textEvidence;
}
