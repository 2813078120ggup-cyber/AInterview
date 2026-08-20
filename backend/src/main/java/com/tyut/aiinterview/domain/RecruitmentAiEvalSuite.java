package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_eval_suite")
public class RecruitmentAiEvalSuite {
    private Long id;
    private String suiteCode;
    private String name;
    private String evaluationType;
    private String promptCode;
    private String description;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
