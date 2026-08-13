package com.tyut.aiinterview.algorithmworker.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_user_progress")
public class AlgorithmUserProgress {
    private Long id;
    private Long userId;
    private Long problemId;
    private String progressStatus;
    private Integer submitCount;
    private LocalDateTime firstAcceptedAt;
    private LocalDateTime lastSubmittedAt;
    private Long bestExecutionTimeMs;
    private Long bestMemoryUsageKb;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
