package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("feedback_ticket")
public class FeedbackTicket {
    public static final String TYPE_INTERVIEW_FAILURE = "INTERVIEW_FAILURE";
    public static final String TYPE_FEATURE_SUGGESTION = "FEATURE_SUGGESTION";
    public static final String TYPE_BUG_REPORT = "BUG_REPORT";
    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private Long id;
    private String ticketNo;
    private Long creatorId;
    private String ticketType;
    private String title;
    private String description;
    private String status;
    private Long assigneeId;
    private String resolution;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime processingAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
