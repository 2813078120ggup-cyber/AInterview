package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("report")
public class Report {
    private Long id;
    private Long interviewId;
    private BigDecimal totalScore;
    private BigDecimal professionalScore;
    private BigDecimal expressionScore;
    private BigDecimal logicScore;
    private BigDecimal adaptabilityScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String improvementSuggestions;
    private String generationMethod;
    private String scoringPromptCode;
    private Integer scoringPromptVersionNo;
    private String reportPromptCode;
    private Integer reportPromptVersionNo;
    private Long generatedBy;
    private String pdfUrl;
    private Integer status;
    private LocalDateTime publishedAt;
}
