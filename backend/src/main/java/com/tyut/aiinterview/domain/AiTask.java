package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_task")
public class AiTask {
    private Long id;
    private Long interviewId;
    private Long answerId;
    private String taskType;
    private String dedupeKey;
    private String status;
    private Integer attempts;
    private Integer maxAttempts;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String inputPayload;
    private String outputPayload;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
