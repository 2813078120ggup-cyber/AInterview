package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("candidate_resume_analysis")
public class CandidateResumeAnalysis {
    private Long id;
    private Long resumeId;
    private Integer analysisVersion;
    private Long aiTaskId;
    private String status;
    private String extractorVersion;
    private String redactionVersion;
    private String redactionSummary;
    private String extractedText;
    private String profileJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
