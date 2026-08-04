package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_submission")
public class AlgorithmSubmission {
    private Long id;
    private Long userId;
    private Long problemId;
    private String language;
    private String sourceCode;
    private String submitType;
    private String status;
    private Integer score;
    private Integer passedCount;
    private Integer totalCount;
    private Long executionTimeMs;
    private Long memoryUsageKb;
    private String compileMessage;
    private String runtimeMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
