package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_case_result")
public class AlgorithmCaseResult {
    private Long id;
    private Long submissionId;
    private Long testCaseId;
    private String status;
    private String actualOutput;
    private Long executionTimeMs;
    private Long memoryUsageKb;
    private LocalDateTime createdAt;
}
