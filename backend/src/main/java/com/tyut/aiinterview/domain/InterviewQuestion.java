package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_question")
public class InterviewQuestion {
    private Long id;
    private Long interviewId;
    private Long questionId;
    private Integer sequenceNo;
    private BigDecimal maxScore;
    private String questionSnapshot;
    private String source;
    private LocalDateTime createdAt;
}
