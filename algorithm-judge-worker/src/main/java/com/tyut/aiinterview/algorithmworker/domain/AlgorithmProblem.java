package com.tyut.aiinterview.algorithmworker.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_problem")
public class AlgorithmProblem {
    private Long id;
    private String title;
    private String slug;
    private String difficulty;
    private String descriptionMd;
    private String inputDescription;
    private String outputDescription;
    private String constraintsDescription;
    private String hintContent;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private String defaultLanguage;
    private String starterCode;
    private String solutionCode;
    private Integer status;
    private Integer sortNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
