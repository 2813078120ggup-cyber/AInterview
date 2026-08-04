package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_reflection")
public class InterviewReflection {
    private Long id;
    private Long interviewId;
    private Long candidateId;
    private Integer selfScore;
    private Integer confidenceLevel;
    private String content;
    private String highlights;
    private String improvements;
    private String actionPlan;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
