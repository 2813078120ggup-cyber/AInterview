package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_test_case")
public class AlgorithmTestCase {
    private Long id;
    private Long problemId;
    private String inputData;
    private String expectedOutput;
    private String caseType;
    private Integer score;
    private Integer sortNo;
    private Integer enabled;
    private LocalDateTime createdAt;
}
