package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("feedback_ticket_attachment")
public class FeedbackTicketAttachment {
    private Long id;
    private Long ticketId;
    private Long activityId;
    private Long mediaId;
    private Long uploaderId;
    private LocalDateTime createdAt;
}
