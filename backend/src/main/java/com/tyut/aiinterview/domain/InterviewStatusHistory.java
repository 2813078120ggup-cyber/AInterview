package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_status_history")
public class InterviewStatusHistory {
    private Long id;
    private String interviewKind;
    private Long interviewId;
    private Long offlineInterviewId;
    private Long applicationId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String reason;
    private String notificationStatus;
    private LocalDateTime createdAt;
}
