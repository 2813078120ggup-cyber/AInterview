package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
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
}
