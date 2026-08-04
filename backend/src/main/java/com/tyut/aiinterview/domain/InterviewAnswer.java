package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_answer")
public class InterviewAnswer {
    private Long id;
    private Long interviewQuestionId;
    private Long mediaId;
    private String answerContent;
    private String answerData;
    private String transcript;
    private String audioUrl;
    private Integer durationSeconds;
    private LocalDateTime answeredAt;
}
