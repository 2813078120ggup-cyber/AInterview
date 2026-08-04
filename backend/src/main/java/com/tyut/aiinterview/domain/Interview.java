package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview")
public class Interview {
    public static final int PENDING = 0;
    public static final int IN_PROGRESS = 1;
    public static final int COMPLETED = 2;
    public static final int CANCELLED = 3;
    public static final int PASSED = 4;
    public static final int REPORT_GENERATING = 5;
    public static final int REPORT_READY = 6;
    public static final int FAILED = 7;
    private Long id;
    private Long positionId;
    private String title;
    private Long candidateId;
    private Long interviewerId;
    private LocalDateTime scheduledAt;
    private Integer duration;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer activeQuestionIndex;
    private LocalDateTime progressUpdatedAt;
    private Integer status;
    private String type;
    private String meetingUrl;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
