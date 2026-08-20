package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_match_evaluation")
public class JobMatchEvaluation {
    private Long id;
    private Long applicationId;
    private Long analysisId;
    private Long aiTaskId;
    private Integer evaluationVersion;
    private Integer resumeVersion;
    private String status;
    private String positionSnapshot;
    private String resumeSnapshot;
    private String redactionVersion;
    private String redactionSummary;
    private BigDecimal ruleScore;
    private BigDecimal aiScore;
    private BigDecimal finalScore;
    private String summary;
    private String ruleMatchedSkills;
    private String matchedSkills;
    private String strengths;
    private String gaps;
    private String risks;
    private String evidence;
    private String recommendation;
    private String confidence;
    private Integer humanReviewRequired;
    private String humanReviewStatus;
    private String humanReviewDecision;
    private String humanReviewNote;
    private Long humanReviewedBy;
    private LocalDateTime humanReviewedAt;
    private String providerName;
    private String modelName;
    private Integer promptVersion;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
