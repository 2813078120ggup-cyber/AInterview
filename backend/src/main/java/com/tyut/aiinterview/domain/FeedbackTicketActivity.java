package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("feedback_ticket_activity")
public class FeedbackTicketActivity {
    public static final String COMMENT = "COMMENT";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ASSIGNMENT = "ASSIGNMENT";
    public static final String SUBMITTED = "SUBMITTED";

    private Long id;
    private Long ticketId;
    private Long actorId;
    private String activityType;
    private String content;
    private String fromStatus;
    private String toStatus;
    private Long fromAssigneeId;
    private Long toAssigneeId;
    private String clientRequestId;
    private LocalDateTime createdAt;
}
