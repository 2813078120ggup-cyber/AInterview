package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_timeline_event")
public class InterviewTimelineEvent {
    private Long id;
    private Long recordingId;
    private Long interviewQuestionId;
    private String eventType;
    private Long offsetMs;
    private String content;
    private LocalDateTime createdAt;
}
