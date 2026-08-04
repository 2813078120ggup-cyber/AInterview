package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_recording")
public class InterviewRecording {
    private Long id;
    private Long interviewId;
    private String mode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
