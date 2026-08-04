package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("free_interview_session")
public class FreeInterviewSession {
    public static final String ANALYZING = "ANALYZING";
    public static final String INTERVIEWING = "INTERVIEWING";
    public static final String REPORT_GENERATING = "REPORT_GENERATING";
    public static final String REPORT_READY = "REPORT_READY";
    public static final String FAILED = "FAILED";

    private Long id;
    private Long candidateId;
    private String resumeFilename;
    private String targetRole;
    private String resumeText;
    private String resumeSummary;
    private String status;
    private BigDecimal totalScore;
    private BigDecimal professionalScore;
    private BigDecimal expressionScore;
    private BigDecimal logicScore;
    private BigDecimal adaptabilityScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String improvementSuggestions;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
