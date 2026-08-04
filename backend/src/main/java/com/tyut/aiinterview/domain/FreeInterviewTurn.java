package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("free_interview_turn")
public class FreeInterviewTurn {
    private Long id;
    private Long sessionId;
    private Integer turnNo;
    private String submissionKey;
    private String question;
    private String answer;
    private String nextQuestion;
    private LocalDateTime createdAt;
}
